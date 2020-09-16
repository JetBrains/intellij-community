android {
  defaultConfig {
    consumerProguardFiles("proguard-android.txt")
    proguardFiles("proguard-android.txt", "proguard-rules.pro")
    resConfigs("abcd")
    resValue("mnop", "qrst", "uvwx")
  }
}
