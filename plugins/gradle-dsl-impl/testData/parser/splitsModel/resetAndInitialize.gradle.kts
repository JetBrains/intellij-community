android {
  splits {
    abi {
      include("abi-include-1")
      reset()
      include("abi-include-2", "abi-include-3")
    }
    density {
      include("density-include-1", "density-include-2")
      reset()
      include("density-include-3")
    }
  }
}
