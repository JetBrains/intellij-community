android {
  flavorDimensions("tier")
  productFlavors {
    create("demo") {
      setDimension("tier")
      setMatchingFallbacks(listOf("trial"))
    }
  }
}
