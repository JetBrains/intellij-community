android {
  defaultConfig {
    setApplicationId("com.example.myapplication")
    consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
    setDimension("abcd")
    setManifestPlaceholders(mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"))
    maxSdkVersion(23)
    minSdkVersion(15)
    setMultiDexEnabled(true)
    multiDexKeepFile = file("multidex.keep")
    multiDexKeepProguard = file("multidex.proguard")
    proguardFiles("proguard-android.txt", "proguard-rules.pro")
    renderscriptTargetApi = 18
    renderscriptSupportModeEnabled = true
    renderscriptSupportModeBlasEnabled = false
    renderscriptNdkModeEnabled = true
    resConfigs("abcd", "efgh")
    resValue("abcd", "efgh", "ijkl")
    targetSdkVersion(22)
    setTestApplicationId("com.example.myapplication.test")
    setTestFunctionalTest(true)
    setTestHandleProfiling(true)
    testInstrumentationRunner("abcd")
    testInstrumentationRunnerArguments(mapOf("size" to "medium", "foo" to "bar"))
    useJack(true)
    vectorDrawables {
      useSupportLibrary = true
    }
    setVersionCode(1)
    setVersionName("1.0")
    wearAppUnbundled = true
  }
}
