android {
  splits {
    abi {
      exclude("abi-exclude")
      include("abi-include")
    }
    density {
      compatibleScreens("screen")
      exclude("density-exclude")
      include("density-include")
    }
    language {
      include("language-include")
    }
  }
}
