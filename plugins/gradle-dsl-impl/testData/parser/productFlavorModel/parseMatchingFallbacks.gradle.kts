android {
  productFlavors {
    create("demo") {
      setMatchingFallbacks(listOf("trial", "free"))
    }
  }
}
