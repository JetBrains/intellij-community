android.defaultConfig {
  proguardFiles(listOf("pro-1.txt", "pro-2.txt"))
  resConfigs("abcd", "efgh")
  resValue("abcd", "efgh", "ijkl")
  testInstrumentationRunnerArguments = mutableMapOf("key1" to "value1", "key2" to "value2")
  testInstrumentationRunnerArgument("key3", "value3")
}
android {
  defaultConfig {
    setManifestPlaceholders(mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"))
    proguardFile("pro-3.txt")
    resConfigs("ijkl", "mnop")
    resValue("mnop", "qrst", "uvwx")
    testInstrumentationRunnerArguments(mapOf("key4" to "value4", "key5" to "value5"))
  }
}
android.defaultConfig.manifestPlaceholders["activityLabel3"] = "defaultName3"
android.defaultConfig.manifestPlaceholders["activityLabel4"] = "defaultName4"
android.defaultConfig.proguardFiles("pro-4.txt", "pro-5.txt")
android.defaultConfig.resConfig("qrst")
android.defaultConfig.testInstrumentationRunnerArguments["key6"] = "value6"
android.defaultConfig.testInstrumentationRunnerArguments["key7"] = "value7"
