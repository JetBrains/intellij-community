android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mapOf("key1" to "value1", "key2" to "value2")
    }
  }
}
