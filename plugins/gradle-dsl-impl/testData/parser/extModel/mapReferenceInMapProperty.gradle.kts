val TEST_MAP by extra(mapOf("test1" to "value1", "test2" to "value2"))

android {
  defaultConfig {
    testInstrumentationRunnerArguments(extra["TEST_MAP"])
  }
}
