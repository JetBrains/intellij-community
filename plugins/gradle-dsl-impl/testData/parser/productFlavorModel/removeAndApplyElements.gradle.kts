android {
  defaultConfig {
    setApplicationId("com.example.myapplication")
    consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
    setDimension("abcd")
    manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
    maxSdkVersion(23)
    minSdkVersion(15)
    setMultiDexEnabled(true)
    proguardFiles("proguard-android.txt", "proguard-rules.pro")
    resConfigs("abcd", "efgh")
    resValue("abcd", "efgh", "ijkl")
    targetSdkVersion(22)
    setTestApplicationId("com.example.myapplication.test")
    setTestFunctionalTest(false)
    setTestHandleProfiling(true)
    testInstrumentationRunner("abcd")
    testInstrumentationRunnerArguments(mapOf("size" to "medium","foo" to "bar"))
    useJack(false)
    setVersionCode(1)
    setVersionName("1.0")
  }
}
