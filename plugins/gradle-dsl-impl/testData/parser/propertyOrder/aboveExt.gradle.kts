android {
  defaultConfig {
    minSdkVersion(extra["minSdk"] as Int)
    maxSdkVersion(extra["maxSdk"] as Int)
  }
}

val minSdk by extra(14)
val maxSdk by extra(18)
