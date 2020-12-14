android {
  defaultConfig {
    minSdkVersion(extra["minSdk"])
    maxSdkVersion(extra["maxSdk"])
  }
}

val minSdk by extra(14)
val maxSdk by extra(18)
