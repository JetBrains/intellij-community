android {
  buildToolsVersion = "23.0.0"
  compileSdkVersion(23)
  defaultPublishConfig = "debug"
  dynamicFeatures = mutableSetOf(":f1", ":f2")
  flavorDimensions("abi", "version")
  generatePureSplits = true
  setPublishNonDefault(false)
  resourcePrefix("abcd")
}
android.buildToolsVersion = "21.0.0"
android.compileSdkVersion = "android-21"
android.defaultPublishConfig = "release"
android.dynamicFeatures = mutableSetOf(":g1", ":g2")
android.flavorDimensions("abi1", "version1")
android.generatePureSplits = false
android.setPublishNonDefault(true)
android.resourcePrefix("efgh")
