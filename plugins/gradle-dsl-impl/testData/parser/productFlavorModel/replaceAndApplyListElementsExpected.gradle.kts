android {
  defaultConfig {
    consumerProguardFiles("proguard-android-1.txt", "proguard-rules.pro")
    proguardFiles("proguard-android-1.txt", "proguard-rules.pro")
    resConfigs("xyz", "efgh")
    resValue("abcd", "mnop", "qrst")
  }
}
