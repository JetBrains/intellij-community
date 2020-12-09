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
    targetSdkVersion(22)
    setTestApplicationId("com.example.myapplication.test")
    setTestFunctionalTest(true)
    testHandleProfiling = false
    testInstrumentationRunner = "abcd"
    testInstrumentationRunnerArguments = mutableMapOf("size" to "medium", "foo" to "bar")
    useJack(true)
    setVersionCode(1)
    versionName("1.0")
  }
}
android.defaultConfig {
  applicationId = "com.example.myapplication1"
  setConsumerProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.pro"))
  setDimension("efgh")
  setManifestPlaceholders(mapOf("activityLabel3" to "defaultName3", "activityLabel4" to "defaultName4"))
  maxSdkVersion = 24
  minSdkVersion(16)
  setMultiDexEnabled(false)
  setProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.pro"))
  targetSdkVersion(23)
  testApplicationId = "com.example.myapplication.test1"
  setTestFunctionalTest(false)
  setTestHandleProfiling(true)
  testInstrumentationRunner = "efgh"
  testInstrumentationRunnerArguments = mutableMapOf("key" to "value")
  useJack = false
  setVersionCode(2)
  versionName = "2.0"
}
android.defaultConfig.versionName = "3.0"
