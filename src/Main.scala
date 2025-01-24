import io.circe.parser.decode
import io.circe.syntax._
import models.GeoJson
import models.GeoJson._
import models.GeoJsonCodec._
import org.apache.sedona.spark.SedonaContext
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.sedona_sql.expressions.st_functions.{ST_Area, ST_Centroid}
import org.locationtech.jts.geom._
import org.wololo.jts2geojson.GeoJSONReader


object Main {

  private def fixLinearRingNotClosing(geoJsonStr: String, id: String): String = {
    try {
      val geoJson = decode[GeoJson](geoJsonStr)
      val newGeom: GeoJson = geoJson match {
        case Right(multiPolygon: GeoJson.MultiPolygon) => GeoJson.MultiPolygon(multiPolygon.coordinates.map((pc: PolygonCoordinates) => pc.map(fixLinearRingNotClosing)))
        case Right(polygon: GeoJson.Polygon) => GeoJson.Polygon(polygon.coordinates.map(fixLinearRingNotClosing))

        case Left(error) =>
          println(error.getMessage)
          throw error
      }

      newGeom.asJson.noSpaces
    } catch {
      case e: Exception =>
        println(e.getMessage + " line 34")
        println(geoJsonStr)
        println(id)
        geoJsonStr
    }
  }

  private def fixLinearRingNotClosing(coordinates: LineStringCoordinates): LineStringCoordinates = {
    if (coordinates.head != coordinates.last)
      coordinates :+ coordinates.head
    else coordinates
  }

  private def parseGeoJsonToGeometry(geoJsonStr: String, id: String): Option[Geometry] = {
    try {
      val reader = new GeoJSONReader()
      val geom = reader.read(geoJsonStr)

      Some(geom)
    } catch {
      case e: IllegalArgumentException if e.getMessage == "Points of LinearRing do not form a closed linestring" =>
        val pip = parseGeoJsonToGeometry(fixLinearRingNotClosing(geoJsonStr, id), id)
        pip
      case e: Exception =>
        println(e.getMessage + " line 56")
        println(geoJsonStr)
        println(id)
        throw e
    }
  }

//  private def fixGeometry(geometry: Geometry, id: String): Option[Geometry] = {
//    try {
//      geometry match {
//        case multiPolygon: MultiPolygon => Some(FixLogic.fixGeometry(multiPolygon, id))
//        case polygon: Polygon => Some(FixLogic.fixGeometry(polygon, id))
//        case _ => None
//      }
//    } catch {
//      case e: Exception => println(e.getMessage + " line 73")
//        println(e)
//        println(e.getCause)
//        println(e.getStackTrace.mkString("Array(", ", ", ")"))
//        println(geometry)
//        println(geometry.toString)
//        println(id)
//        None
//    }
//  }


  private def GeometryCoordinatesLength(geometry: Geometry): Int = {
    geometry.getCoordinates.length
  }


  case class GeometryDF(geom: Geometry)
  private case class GeoJsonDf(geoJson: String, id: String)
  case class GeomFromWKT(wkt: String)

  def main(args: Array[String]): Unit = {
    val seasonedContext = SedonaContext.builder().master("local[*]").getOrCreate()
    val spark = SedonaContext.create(seasonedContext)

    import spark.implicits._

//    val getRemoveParallelAndRepeated = udf(removeParallelAndRepeated _)
//    val getFixGeometry = udf(fixGeometry _)
    val getParsedGeometry = udf(parseGeoJsonToGeometry _)
    val getGeometryCoordinatesLength = udf(GeometryCoordinatesLength _)

//    val getFinalFix = udf(finalFix _)
//    val getFixedIntersectionOnExistingCoordinate = udf(fixIntersectionOnExistingCoordinate _)


    val geoJSons = Seq(
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[45,6],[35,9],[50,16],[54,3],[50,16],[52.5,6],[54.5,17],[45,6],[27,15.5],[42,13],[45,6],[0,34],[36,9],[50,15],[45,6]]]}", "cannot_even_fix"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-6,0],[-4,4],[0,0],[4,-6],[6,1],[10,7],[13,4],[11,2],[6,1],[5,3],[1,2],[0,0],[-6,0]]]}", "multi_intersection_points_x1"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[-6,0],[-4,4],[0,0],[1,2],[5,3],[6,1],[10,7],[13,4],[11,2],[6,1],[4,-6],[0,0]]]}", "multi_intersection_points_x2"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[-6,0],[-4,4],[0,0],[4,-6],[6,1],[10,7],[13,4],[11,2],[6,1],[5,3],[1,2],[0,0]]]}", "multi_intersection_points_x3"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[4,-6],[6,1],[10,7],[13,4],[11,2],[6,1],[5,3],[1,2],[0,0],[-6,0],[-4,4],[0,0]]]}", "multi_intersection_points_x4"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]]}", "polygon1"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]}", "polygon2"),

//      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]]]}", "multipolygon1"),
//      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]]}", "multipolygon2"),
//      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]],[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]]}", "multipolygon_1_2"),
//
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0]]]}", "polygon1_missing_closing_ring"), // work
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]}", "polygon2_missing_closing_ring"), // no work

//      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0]]]]}", "multipolygon1_missing_closing_ring"), // work
//      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]]}", "multipolygon2_missing_closing_ring"), // no work
//      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]],[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0]]]]}", "multipolygon_1_2_missing_closing_ring"), // no work

      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,-2],[-1.5,-0.5],[0,0],[1.5,0.5],[0,2],[-1.6667,1.6667],[0,0],[1.6,-1.6],[0,-2]]]}", "bowtie_2"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,-2],[-1.6,-0.4],[0,0],[1.5,0.5],[0,2],[-1.6667,1.6667],[0,0],[1.6,-1.6],[0,-2]]]}", "wierd_bowtie_1"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,-2],[-1.5,-0.5],[0,0],[1.6,0.4],[0,2],[-1.6667,1.6667],[0,0],[1.6,-1.6],[0,-2]]]}", "wierd_bowtie_2"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,8],[5,4],[4,1],[0,0],[-3,0],[-6,-3],[-5,-5],[-2,-4],[0,0],[-4,4],[-1,5],[0,0]]]}", "extra_polygon"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-6,0],[-4,4],[0,0],[5,3],[6,1],[10,7],[11,2],[6,1],[0,0],[-6,0]]]}", "multi_intersection_points_1"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-6,0],[-4,4],[0,0],[1,2],[5,3],[6,1],[10,7],[11,2],[6,1],[0,0],[-6,0]]]}", "multi_intersection_points_2"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-6,0],[-4,4],[0,0],[1,2],[5,3],[6,1],[10,7],[13,4],[11,2],[6,1],[0,0],[-6,0]]]}", "multi_intersection_points_3"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-6,0],[-4,4],[0,0],[1,2],[5,3],[6,1],[10,7],[13,4],[11,2],[6,1],[4,-6],[0,0],[-6,0]]]}", "multi_intersection_points_4")
    ).toDF()

    val fixedPolygonsDF = geoJSons.withColumn("geometry", getParsedGeometry(col("geoJson"), col("id")))
//      .withColumn("fixed", getFixGeometry(col("geometry"), col("id")))
//      .withColumn("no_parallel", getRemoveParallelAndRepeated(col("geometry")))
//      .withColumn("fixed1", getFixGeometry(col("geometry"), col("id")))
//      .withColumn("fixed2", getFixGeometry(col("geometry"), col("id")))
//      .withColumn("fixed3", getFixGeometry(col("geometry"), col("id")))
//      .withColumn("fixed4", getFixGeometry(col("geometry"), col("id")))

    //      .withColumn("fixed", getFinalFix(col("geometry"), col("id")))
//      .withColumn("fixed", getFixedIntersectionOnExistingCoordinate(col("geometry"), col("id")))
      .withColumn("area", ST_Area(col("geometry")))
      .withColumn("centroid", ST_Centroid(col("geometry")))
//      .withColumn("centroid1", ST_Centroid(col("fixed2")))
//      .withColumn("centroid2", ST_Centroid(col("fixed3")))
//      .withColumn("centroid3", ST_Centroid(col("fixed4")))
//      .withColumn("original_coordinates", getGeometryCoordinatesLength(col("geometry")))
//      .withColumn("fixed_coordinates", getGeometryCoordinatesLength(col("fixed")))


    fixedPolygonsDF.show(false)


//    fixedPolygonsDF.select(col("id"), col("geometry"), col("fixed"), col("centroid"), col("fixed2"), col("centroid1"),
//      col("fixed3"), col("centroid2"), col("fixed4"), col("centroid3")).show(false)

//    val wktDf = Seq(GeomFromWKT("POLYGON ((2.49939 0.332335, 2.905884 0.315855, 2.90863 -0.019226, 1.991272 -0.00824, 2.026978 -0.42297, 2.504883 -0.398251, 2.502136 -0.013733, 2.49939 0.332335))"))
//      .toDF().withColumn("geom", ST_GeomFromText(col("wkt"))).withColumn("geoJson", ST_AsGeoJSON(col("geom")))
//
//    wktDf.select(col("geoJson")).show(false)


    println("FINISH")

  }
}