android {
  packagingOptions {
    excludes = setOf("exclude1", "exclude2")
    exclude("exclude3")
    merges = setOf("merge1", "merge2")
    merge("merge3")
    pickFirsts = setOf("pickFirst1", "pickFirst2")
    pickFirst("pickFirst3")
  }
}
