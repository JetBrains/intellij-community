val TEST_STRING by extra("test")
android {
  defaultConfig {
    proguardFiles(listOf("proguard-android.txt", extra["TEST_STRING"]))
  }
}
