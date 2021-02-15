android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mutableMapOf("key1" to 12345, "key2" to "value2", "key3" to true)
    }
  }
}
