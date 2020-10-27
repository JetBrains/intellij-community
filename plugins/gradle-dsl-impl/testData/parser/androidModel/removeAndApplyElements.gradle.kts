android {
  buildToolsVersion("23.0.0")
  compileSdkVersion("23")
  defaultPublishConfig("debug")
  dynamicFeatures = mutableSetOf(":f1", ":f2")
  flavorDimensions("abi", "version")
  generatePureSplits(true)
  setPublishNonDefault(false)
  resourcePrefix("abcd")
}
