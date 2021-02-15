android {
  defaultConfig {
    manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
    testInstrumentationRunnerArguments(mapOf("size" to "medium", "foo" to "bar"))
  }
}
