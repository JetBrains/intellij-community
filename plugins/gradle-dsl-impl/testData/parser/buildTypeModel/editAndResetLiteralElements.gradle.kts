android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix" 
      isDebuggable = true
      isEmbedMicroApp = false
      isJniDebuggable = true
      isMinifyEnabled = false
      multiDexEnabled = true
      isPseudoLocalesEnabled = false
      isRenderscriptDebuggable = true
      renderscriptOptimLevel = 1
      isShrinkResources = false
      isTestCoverageEnabled = true
      useJack = false
      versionNameSuffix = "abc"
      isZipAlignEnabled = true
    }
  }
}
