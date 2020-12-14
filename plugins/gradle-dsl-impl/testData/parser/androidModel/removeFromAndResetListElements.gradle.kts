android {
  dynamicFeatures = mutableSetOf(":f1", ":f2")
  flavorDimensions("abi", "version")
}
