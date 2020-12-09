android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix-1"
      isDebuggable = false
      isEmbedMicroApp = true
      isJniDebuggable = false
      isMinifyEnabled = true
      multiDexEnabled = false
      isPseudoLocalesEnabled = true
      isRenderscriptDebuggable = false
      renderscriptOptimLevel = 2
      isShrinkResources = true
      isTestCoverageEnabled = false
      useJack = true
      versionNameSuffix = "def"
      isZipAlignEnabled = false
    }
  }
}
