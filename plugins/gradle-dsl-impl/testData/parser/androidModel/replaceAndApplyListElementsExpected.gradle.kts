android {
  dynamicFeatures = mutableSetOf(":f1", ":g2")
  flavorDimensions("xyz", "version")
}
