extra["VERSION"] = "3.2.0"

buildscript {
  val VERSION by extra("2.1.2")
  dependencies {
    classpath("com.android.tools.build:gradle:${project.extra["VERSION"]}")
  }
}
android {
  defaultConfig {
    applicationId = project.extra["VERSION"] as String
  }
}
