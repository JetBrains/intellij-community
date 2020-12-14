android {
  compileSdkVersion = "android-23"
  defaultConfig {
    minSdkVersion(project(":<SUB_MODULE_NAME>").android.compileSdkVersion)
  }
}
