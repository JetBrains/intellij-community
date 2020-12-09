android {
  packagingOptions {
    exclude("exclude1")
    merges = mutableSetOf("merge1")
    pickFirsts = mutableSetOf()
    pickFirst("pickFirst1")
  }
}
