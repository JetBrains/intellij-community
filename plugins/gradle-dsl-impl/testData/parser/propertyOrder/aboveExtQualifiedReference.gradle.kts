android {
  defaultConfig {
    minSdkVersion(extra["minSdk"] as Int)
    maxSdkVersion(extra["maxSdk"] as Int)
  }
}

extra["minSdk"] = 14
extra["maxSdk"] = 18
