val constants by extra(mapOf("COMPILE_SDK_VERSION" to 21))

android {
  compileSdkVersion(constants["COMPILE_SDK_VERSION"]!!)
}
