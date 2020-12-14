val SDK_VERSION by extra(21)
val COMPILE_SDK_VERSION by extra(extra["SDK_VERSION"])

android {
  compileSdkVersion(extra["COMPILE_SDK_VERSION"])
  defaultConfig {
    targetSdkVersion(compileSdkVersion)
  }
}
