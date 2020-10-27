val ANDROID by extra("android")
val SDK_VERSION by extra(23)
android {
  compileSdkVersion("${extra["ANDROID"]}-${extra["SDK_VERSION"]}")
  defaultConfig {
    targetSdkVersion("$compileSdkVersion")
  }
}
