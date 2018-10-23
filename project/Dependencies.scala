package ml.combust.mleap

import sbt._
import Keys._

object Dependencies {
  import DependencyHelpers._

  val sparkVersion = "2.4.0"
  val scalaTestVersion = "3.0.0"
  val tensorflowVersion = "1.7.0"
  val akkaVersion = "2.4.16"
  val akkaHttpVersion = "10.0.3"
  val xgboostVersion = "0.80"
  val hadoopVersion = "2.7.0" // matches spark version

  object Compile {
    val sparkMllibLocal = "org.apache.spark" %% "spark-mllib-local" % sparkVersion from "file://../lib/spark-mllib-local_2.12-2.4.0.jar" excludeAll(ExclusionRule(organization = "org.scalatest"))
    val spark = Seq("org.apache.spark" %% "spark-core" % sparkVersion from "file://../lib/spark-mllib-local_2.12-2.4.0.jar",
      "org.apache.spark" %% "spark-sql" % sparkVersion from "file://../lib/spark-core_2.12-2.4.0.jar",
      "org.apache.spark" %% "spark-mllib" % sparkVersion from "file://../lib/spark-mllib_2.12-2.4.0.jar",
      "org.apache.spark" %% "spark-mllib-local" % sparkVersion from "file://../lib/spark-mllib-local_2.12-2.4.0.jar",
      "org.apache.spark" %% "spark-catalyst" % sparkVersion from "file://../lib/spark-catalyst_2.12-2.4.0.jar")
    val avroDep = "org.apache.avro" % "avro" % "1.8.1"
    val sprayJson = "io.spray" %% "spray-json" % "1.3.2"
    val arm = "com.jsuereth" %% "scala-arm" % "2.0"
    val config = "com.typesafe" % "config" % "1.3.0"
    val scalaReflect = ScalaVersionDependentModuleID.versioned("org.scala-lang" % "scala-reflect" % _)
    //    val sparkAvro = "com.databricks" %% "spark-avro" % "2.4.0"
    val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion
    val jTransform = "com.github.rwl" % "jtransforms" % "2.4.0" exclude("junit", "junit")
    val tensorflowDeps = Seq(
      "org.tensorflow" % "libtensorflow" % tensorflowVersion,
      "org.tensorflow" % "libtensorflow_jni" % tensorflowVersion
    )
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
    val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
    val scalameter = "com.storm-enroute" %% "scalameter" % "0.8.2"
    val scopt = "com.github.scopt" %% "scopt" % "3.5.0"
    val xgboostDep = "ml.dmlc" % "xgboost4j" % xgboostVersion
    val xgboostSparkDep = "ml.dmlc" % "xgboost4j-spark" % xgboostVersion
    val hadoop = "org.apache.hadoop" % "hadoop-client" % hadoopVersion
    // https://mvnrepository.com/artifact/org.scalanlp/breeze
    val breeze  = "org.scalanlp" %% "breeze" % "1.0-RC2"

  }

  object Test {
    val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    val akkaHttpTestkit =  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"
  }

  object Provided {
    val spark = Compile.spark.map(_.excludeAll(ExclusionRule(organization = "org.scalatest"))).map(_ % "provided")
    val hadoop = Compile.hadoop % "provided"
  }

  import Compile._
  val l = libraryDependencies

  val tensor = l ++= Seq(sprayJson, Test.scalaTest)

  val bundleMl = l ++= Seq(arm, config, sprayJson, Test.scalaTest)

  val bundleHdfs = l ++= Seq(Provided.hadoop, Test.scalaTest)

  val base = l ++= Seq()

  val core = l ++= Seq(sparkMllibLocal, jTransform, Test.scalaTest, breeze)

  def runtime(scalaVersion: SettingKey[String]) = l ++= (Seq(Test.scalaTest) ++ scalaReflect.modules(scalaVersion.value))

  val sparkBase = l ++= Provided.spark ++ Seq(Test.scalaTest)

  val sparkTestkit = l ++= Provided.spark ++ Seq(scalaTest) // Seq(sparkAvro, scalaTest)

  val spark = l ++= Provided.spark

  val sparkExtension = l ++= Provided.spark ++ Seq(Test.scalaTest)

  val avro = l ++= Seq(avroDep, Test.scalaTest)

  val tensorflow = l ++= tensorflowDeps ++ Seq(Test.scalaTest)

  val xgboostRuntime = l ++= Seq(xgboostDep) ++ Seq(Test.scalaTest)

  val xgboostSpark = l ++= Seq(xgboostSparkDep) ++ Provided.spark

  val serving = l ++= Seq(akkaHttp, akkaHttpSprayJson, config, Test.scalaTest, Test.akkaHttpTestkit)

  val benchmark = l ++= Seq(scalameter, scopt) ++ Compile.spark

  val databricksRuntimeTestkit = l ++= Provided.spark ++ Seq(xgboostSparkDep)

  object DependencyHelpers {
    case class ScalaVersionDependentModuleID(modules: String => Seq[ModuleID]) {
      def %(config: String): ScalaVersionDependentModuleID =
        ScalaVersionDependentModuleID(version => modules(version).map(_ % config))
    }
    object ScalaVersionDependentModuleID {
      implicit def liftConstantModule(mod: ModuleID): ScalaVersionDependentModuleID = versioned(_ => mod)

      def versioned(f: String => ModuleID): ScalaVersionDependentModuleID = ScalaVersionDependentModuleID(v => Seq(f(v)))
      def fromPF(f: PartialFunction[String, ModuleID]): ScalaVersionDependentModuleID =
        ScalaVersionDependentModuleID(version => if (f.isDefinedAt(version)) Seq(f(version)) else Nil)
    }

    /**
      * Use this as a dependency setting if the dependencies contain both static and Scala-version
      * dependent entries.
      */
    def versionDependentDeps(modules: ScalaVersionDependentModuleID*): Def.Setting[Seq[ModuleID]] =
      libraryDependencies <++= scalaVersion(version => modules.flatMap(m => m.modules(version)))

    val ScalaVersion = """\d\.\d+\.\d+(?:-(?:M|RC)\d+)?""".r
    val nominalScalaVersion: String => String = {
      // matches:
      // 2.12.0-M1
      // 2.12.0-RC1
      // 2.12.0
      case version @ ScalaVersion() => version
      // transforms 2.12.0-custom-version to 2.12.0
      case version => version.takeWhile(_ != '-')
    }
  }
}
