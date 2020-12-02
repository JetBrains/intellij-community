android {
  defaultConfig {
    manifestPlaceholders = mutableMapOf("activityLabel2" to "defaultName2")
    testInstrumentationRunnerArguments(mapOf("foo" to "bar"))
  }
}
