android {
  buildTypes {
    create("xyz") {
    }
  }
}
android.buildTypes.getByName("xyz").setApplicationIdSuffix("mySuffix")
android.buildTypes.getByName("xyz").buildConfigField("abcd", "efgh", "ijkl")
android.buildTypes.getByName("xyz").consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
android.buildTypes.getByName("xyz").isDebuggable = true
android.buildTypes.getByName("xyz").isEmbedMicroApp = true
android.buildTypes.getByName("xyz").setJniDebuggable(true)
android.buildTypes.getByName("xyz").manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
android.buildTypes.getByName("xyz").setMinifyEnabled(true)
android.buildTypes.getByName("xyz").setMultiDexEnabled(true)
android.buildTypes.getByName("xyz").proguardFiles("proguard-android.txt", "proguard-rules.pro")
android.buildTypes.getByName("xyz").isPseudoLocalesEnabled = true
android.buildTypes.getByName("xyz").setRenderscriptDebuggable(true)
android.buildTypes.getByName("xyz").setRenderscriptOptimLevel(1)
android.buildTypes.getByName("xyz").resValue("mnop", "qrst", "uvwx")
android.buildTypes.getByName("xyz").isShrinkResources = true
android.buildTypes.getByName("xyz").isTestCoverageEnabled = true
android.buildTypes.getByName("xyz").useJack(true)
android.buildTypes.getByName("xyz").setVersionNameSuffix("abc")
android.buildTypes.getByName("xyz").setZipAlignEnabled(true)
