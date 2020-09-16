val list by extra(listOf(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"))
android {
  defaultConfig {
    setProguardFiles(list)
  }
}
