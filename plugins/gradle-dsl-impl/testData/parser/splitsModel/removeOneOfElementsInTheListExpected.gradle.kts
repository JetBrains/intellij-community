android {
  splits {
    abi {
      exclude("abi-exclude-2")
      include("abi-include-1")
    }
    density {
      compatibleScreens("screen2")
      exclude("density-exclude-1")
      include("density-include-2")
    }
    language {
      include("language-include-1")
    }
  }
}
