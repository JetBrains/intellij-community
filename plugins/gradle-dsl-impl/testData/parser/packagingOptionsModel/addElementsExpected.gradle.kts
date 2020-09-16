android {
  packagingOptions {
    excludes = mutableSetOf("exclude")
    merges = mutableSetOf("merge1", "merge2")
    pickFirsts = mutableSetOf("pickFirst1", "pickFirst2", "pickFirst3")
  }
}
