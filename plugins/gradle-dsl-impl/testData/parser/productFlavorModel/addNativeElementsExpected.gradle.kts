android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters = mutableSetOf("abiFilterX")
        arguments = listOf("argumentX")
        cFlags = listOf("cFlagX")
        cppFlags = listOf("cppFlagX")
        targets = mutableSetOf("targetX")
      }
      ndkBuild {
        abiFilters = mutableSetOf("abiFilterY")
        arguments = listOf("argumentY")
        cFlags = listOf("cFlagY")
        cppFlags = listOf("cppFlagY")
        targets = mutableSetOf("targetY")
      }
    }
    ndk {
      abiFilters("abiFilterZ")
    }
  }
}
