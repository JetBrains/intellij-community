android {
  dynamicFeatures = mutableSetOf(":f1", ":f2", ":f")
  flavorDimensions("abi", "version", "xyz")
}
