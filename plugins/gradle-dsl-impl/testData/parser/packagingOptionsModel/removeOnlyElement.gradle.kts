android {
  packagingOptions {
    exclude("exclude")
    merges = setOf("merge")
    pickFirsts = setOf()
    pickFirst("pickFirst")
  }
}
