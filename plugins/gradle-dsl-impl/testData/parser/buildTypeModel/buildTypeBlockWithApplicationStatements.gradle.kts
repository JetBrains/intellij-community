android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix"
      buildConfigField("abcd", "efgh", "ijkl")
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      isDebuggable = true
      isEmbedMicroApp = true
      isJniDebuggable = true
      manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
      isMinifyEnabled = true
      multiDexEnabled = true
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      isPseudoLocalesEnabled = true
      isRenderscriptDebuggable = true
      renderscriptOptimLevel = 1
      resValue("mnop", "qrst", "uvwx")
      isShrinkResources = true
      isTestCoverageEnabled = true
      useJack = true
      versionNameSuffix = "abc"
      isZipAlignEnabled = true
    }
  }
}