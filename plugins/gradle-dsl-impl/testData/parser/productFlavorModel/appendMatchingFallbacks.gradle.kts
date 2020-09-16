android {
  productFlavors {
    create("demo") {
      matchingFallbacks = listOf("trial")
    }
  }
}
