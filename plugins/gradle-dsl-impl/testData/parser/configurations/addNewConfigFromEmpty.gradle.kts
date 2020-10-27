android {
  buildToolsVersion = "23.0.0"
  compileSdkVersion(23)
  defaultPublishConfig = "debug"
  generatePureSplits = true
}

dependencies {
  runtime(mapOf("group" to "org.gradle.test.classifiers", "name" to "service", "version" to "1.0", "classifier" to "jdk14", "ext" to "jar"))
}
