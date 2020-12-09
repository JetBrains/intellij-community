android {
  defaultConfig {
    proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    consumerProguardFiles("proguard-rules.pro")
    setProguardFiles(listOf("val1", "val2"))
    setConsumerProguardFiles(listOf("val3", "val4"))
  }
}
