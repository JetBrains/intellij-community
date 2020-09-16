android {
  buildToolsVersion = "23.0.0"
  compileSdkVersion = "android-23"
  defaultPublishConfig = "debug"
  dynamicFeatures = mutableSetOf(":f1", ":f2")
  generatePureSplits = true
}
