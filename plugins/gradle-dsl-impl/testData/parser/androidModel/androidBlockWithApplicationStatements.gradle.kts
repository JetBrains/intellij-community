android {
  buildToolsVersion("23.0.0")
  compileSdkVersion(23)
  defaultPublishConfig("debug")
  flavorDimensions("abi", "version")
  generatePureSplits(true)
  setPublishNonDefault(false)
  resourcePrefix("abcd")
}
