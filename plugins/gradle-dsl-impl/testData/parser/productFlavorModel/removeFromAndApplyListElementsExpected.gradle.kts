android {
  defaultConfig {
    consumerProguardFiles(listOf("proguard-android.txt"))
    proguardFiles(listOf("proguard-android.txt"))
    resConfigs("abcd")
    resValue("abcd", "efgh", "ijkl")
  }
}
