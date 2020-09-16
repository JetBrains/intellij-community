android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        cppFlags("")
        arguments = listOf("-DCMAKE_MAKE_PROGRAM=////")
      }
    }
  }
}
