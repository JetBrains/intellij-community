android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters("abiFilter2")
        arguments = listOf("argument2")
        cFlags("cFlag2")
        cppFlags = listOf("cppFlag2")
        targets("target2")
      }
      ndkBuild {
        abiFilters = setOf("abiFilter4")
        arguments("argument4")
        cFlags = listOf("cFlag4")
        cppFlags("cppFlag4")
        targets = setOf("target4")
      }
    }
    ndk {
      abiFilters("abiFilter5")
      abiFilters("abiFilter7")
    }
  }
}
