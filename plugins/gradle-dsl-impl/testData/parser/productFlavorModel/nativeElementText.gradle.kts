android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters("abiFilter1", "abiFilter2")
        arguments = listOf("argument1", "argument2")
        cFlags("cFlag1", "cFlag2")
        cppFlags = listOf("cppFlag1", "cppFlag2")
        targets("target1", "target2")
      }
      ndkBuild {
        abiFilters = setOf("abiFilter3", "abiFilter4")
        arguments("argument3", "argument4")
        cFlags = listOf("cFlag3", "cFlag4")
        cppFlags("cppFlag3", "cppFlag4")
        targets = setOf("target3", "target4")
      }
    }
    ndk {
      abiFilters("abiFilter5")
      abiFilter("abiFilter6")
      abiFilters("abiFilter7")
    }
  }
}
