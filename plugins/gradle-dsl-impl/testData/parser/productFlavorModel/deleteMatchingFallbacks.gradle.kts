android {
  flavorDimensions("tier")
  productFlavors {
    create("demo") {
      setDimension("tier")
      matchingFallbacks = listOf("trial")
    }
  }
}
