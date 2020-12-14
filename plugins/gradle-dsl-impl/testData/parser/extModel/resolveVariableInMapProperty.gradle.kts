val TEST_STRING by extra("test")
android.defaultConfig {
  testInstrumentationRunnerArguments(mapOf("size" to "medium", "foo" to "${extra["TEST_STRING"]}"))
}
