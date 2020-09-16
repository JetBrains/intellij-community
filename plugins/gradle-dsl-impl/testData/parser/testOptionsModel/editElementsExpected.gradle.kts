android {
  testOptions {
    reportDir = "otherReportDir"
    resultsDir = "otherResultsDir"
    unitTests.isReturnDefaultValues = false
    execution = "ANDROID_TEST_ORCHESTRATOR"
  }
}
