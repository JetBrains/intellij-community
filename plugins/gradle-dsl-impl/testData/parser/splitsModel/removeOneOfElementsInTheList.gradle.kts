android {
  splits {
    abi {
      exclude("abi-exclude-1", "abi-exclude-2")
      include("abi-include-1", "abi-include-2")
    }
    density {
      compatibleScreens("screen1", "screen2")
      exclude("density-exclude-1", "density-exclude-2")
      include("density-include-1", "density-include-2")
    }
    language {
      include("language-include-1", "language-include-2")
    }
  }
}
