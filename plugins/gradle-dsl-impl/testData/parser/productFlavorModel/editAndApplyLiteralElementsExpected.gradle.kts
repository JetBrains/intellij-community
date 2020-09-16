android {
  defaultConfig {
    setApplicationId("com.example.myapplication-1")
    setDimension("efgh")
    maxSdkVersion(24)
    minSdkVersion("16")
    setMultiDexEnabled(false)
    targetSdkVersion("23")
    setTestApplicationId("com.example.myapplication-1.test")
    setTestFunctionalTest(true)
    setTestHandleProfiling(false)
    testInstrumentationRunner("efgh")
    useJack(true)
    setVersionCode("2")
    setVersionName("2.0")
  }
}
