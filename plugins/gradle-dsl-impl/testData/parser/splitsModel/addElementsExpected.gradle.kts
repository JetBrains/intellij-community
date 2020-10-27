android {
  splits {
    abi {
      isEnable = true
      exclude("abi-exclude")
      include("abi-include")
      isUniversalApk = false
    }
    density {
      setAuto(false)
      compatibleScreens("screen")
      isEnable = true
      exclude("density-exclude")
      include("density-include", "density-include2")
    }
    language {
      isEnable = false
      include("language-include", "language-include2")
    }
  }
}
