android {
  defaultConfig {
    consumerProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
    proguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
    resConfigs("abcd", "efgh")
    resValue("abcd", "efgh", "ijkl")
    resValue("mnop", "qrst", "uvwx")
  }
}
