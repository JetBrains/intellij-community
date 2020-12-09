android {
  defaultConfig {
    manifestPlaceholders = mutableMapOf("key1" to "value1", "key2" to "value2")
    testInstrumentationRunnerArguments(mapOf("size" to "medium", "foo" to "bar"))
  }
}
