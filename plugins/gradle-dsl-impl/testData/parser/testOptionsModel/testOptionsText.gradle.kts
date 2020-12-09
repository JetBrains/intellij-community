android {
  testOptions {
    reportDir = "reportDirectory"
    resultsDir = "resultsDirectory"
    unitTests.isReturnDefaultValues = true
    execution = "ANDROID_TEST_ORCHESTRATOR"
  }
}
