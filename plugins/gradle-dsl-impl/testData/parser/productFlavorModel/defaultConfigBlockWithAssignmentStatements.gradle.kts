android.defaultConfig {
    applicationId = "com.example.myapplication"
    setConsumerProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
    dimension = "abcd"
    manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
    maxSdkVersion = 23
    multiDexEnabled = true
    setProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
    renderscriptTargetApi = 18
    renderscriptSupportModeEnabled = true
    renderscriptSupportModeBlasEnabled = false
    renderscriptNdkModeEnabled = true
    testApplicationId = "com.example.myapplication.test"
    testFunctionalTest = true
    testHandleProfiling = true
    testInstrumentationRunner = "abcd"
    testInstrumentationRunnerArguments = mapOf("size" to "medium", "foo" to "bar")
    useJack = true
    vectorDrawables {
        setGeneratedDensities(listOf("yes", "no", "maybe"))
        useSupportLibrary = true
    }
    versionCode = 1
    versionName = "1.0"
    wearAppUnbundled = true
}
