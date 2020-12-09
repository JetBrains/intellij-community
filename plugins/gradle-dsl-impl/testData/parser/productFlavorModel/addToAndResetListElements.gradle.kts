android {
  defaultConfig {
    consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
    proguardFiles("proguard-android.txt", "proguard-rules.pro")
    resConfigs("abcd", "efgh")
    resValue("abcd", "efgh", "ijkl")
  }
}
