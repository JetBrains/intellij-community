android {
  defaultConfig {
    missingDimensionStrategy("minApi", "minApi18")
    missingDimensionStrategy("abi", "x86")
  }
  flavorDimensions("tier")
  productFlavors {
    create("free") {
      dimension = "tier"
      missingDimensionStrategy("minApi", "minApi23")
    }
    create("paid") {}
  }
}
