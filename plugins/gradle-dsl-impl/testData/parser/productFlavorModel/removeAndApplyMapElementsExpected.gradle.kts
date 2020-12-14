android {
  defaultConfig {
    manifestPlaceholders = mapOf("activityLabel2" to "defaultName2")
    testInstrumentationRunnerArguments(mapOf("foo" to "bar"))
  }
}
