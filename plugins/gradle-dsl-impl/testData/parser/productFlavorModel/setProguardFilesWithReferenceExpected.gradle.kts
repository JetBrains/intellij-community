val list by extra(listOf("proguard-rules.pro", "value"))
android {
  defaultConfig {
    setProguardFiles(list)
  }
}
