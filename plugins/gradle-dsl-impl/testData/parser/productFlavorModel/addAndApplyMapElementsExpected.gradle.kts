android {
  defaultConfig {
    setManifestPlaceholders(mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"))
    testInstrumentationRunnerArguments(mapOf("size" to "small", "key" to "value"))
  }
}
