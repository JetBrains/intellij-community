android {
  defaultConfig {
    missingDimensionStrategy("minApi", "minApi18")
  }
  flavorDimensions("tier")
  productFlavors {
    create("free") {
      dimension = "tier"
    }
    create("paid") {}
  }
}
