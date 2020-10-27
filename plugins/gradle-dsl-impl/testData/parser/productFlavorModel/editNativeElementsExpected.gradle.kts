android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters("abiFilter1", "abiFilterX")
        arguments = listOf("argument1", "argumentX")
        cFlags("cFlag1", "cFlagX")
        cppFlags = listOf("cppFlag1", "cppFlagX")
        targets("target1", "targetX")
      }
      ndkBuild {
        abiFilters = setOf("abiFilter3", "abiFilterY")
        arguments("argument3", "argumentY")
        cFlags = listOf("cFlag3", "cFlagY")
        cppFlags("cppFlag3", "cppFlagY")
        targets = setOf("target3", "targetY")
      }
    }
    ndk {
      abiFilters("abiFilter5")
      abiFilter("abiFilterZ")
      abiFilters("abiFilter7")
    }
  }
}
