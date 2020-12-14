android {
  splits {
    abi {
      isEnable = true
      exclude("abi-exclude-1", "abi-exclude-2")
      include("abi-include-1", "abi-include-2")
      isUniversalApk = false
    }
    density {
      setAuto(false)
      compatibleScreens("screen1", "screen2")
      isEnable = true
      exclude("density-exclude-1", "density-exclude-2")
      include("density-include-1", "density-include-2")
    }
    language {
      isEnable = false
      include("language-include-1", "language-include-2")
    }
  }
}
