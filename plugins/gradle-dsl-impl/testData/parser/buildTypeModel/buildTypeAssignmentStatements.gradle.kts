android {
  buildTypes {
    create("xyz") {
    }
  }
}
android.buildTypes.getByName("xyz").applicationIdSuffix = "mySuffix"
// can't assign to consumerProguardFiles
android.buildTypes.getByName("xyz").consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
android.buildTypes.getByName("xyz").isDebuggable = true
android.buildTypes.getByName("xyz").isEmbedMicroApp = true
android.buildTypes.getByName("xyz").isJniDebuggable = true
android.buildTypes.getByName("xyz").manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
android.buildTypes.getByName("xyz").isMinifyEnabled = true
android.buildTypes.getByName("xyz").multiDexEnabled = true
// can't assign to proguardFiles
android.buildTypes.getByName("xyz").proguardFiles("proguard-android.txt", "proguard-rules.pro")
android.buildTypes.getByName("xyz").isPseudoLocalesEnabled = true
android.buildTypes.getByName("xyz").isRenderscriptDebuggable = true
android.buildTypes.getByName("xyz").renderscriptOptimLevel = 1
android.buildTypes.getByName("xyz").isShrinkResources = true
android.buildTypes.getByName("xyz").isTestCoverageEnabled = true
// TODO(xof): useJack might not be implemented in newer versions of AGP (it was deprecated in 3.0)
android.buildTypes.getByName("xyz").isUseJack = true
android.buildTypes.getByName("xyz").versionNameSuffix = "abc"
android.buildTypes.getByName("xyz").isZipAlignEnabled = true
