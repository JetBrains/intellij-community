android {
  defaultConfig {
    manifestPlaceholders = mapOf("key1" to 12345, "key2" to "value2", "key3" to true)
    testInstrumentationRunnerArguments(mapOf("size" to "small", "foo" to "bar", "key" to "value"))
  }
}
