android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mutableMapOf("key1" to "value1", "key2" to "value2")
    }
  }
}
