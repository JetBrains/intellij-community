android {
  splits {
    abi {
      isEnable = false
      exclude("abi-exclude-1", "abi-exclude-3")
      include("abi-include-3", "abi-include-2")
      isUniversalApk = true
    }
    density {
      setAuto(true)
      compatibleScreens("screen1", "screen3")
      isEnable = false
      exclude("density-exclude-3", "density-exclude-2")
      include("density-include-1", "density-include-3")
    }
    language {
      isEnable = true
      include("language-include-3", "language-include-2")
    }
  }
}
