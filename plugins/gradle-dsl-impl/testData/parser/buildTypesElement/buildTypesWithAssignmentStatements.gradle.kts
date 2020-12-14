android.buildTypes {
  create("type1") {
    applicationIdSuffix = "suffix1"
    setProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.txt"))
  }
  create("type2") {
    applicationIdSuffix = "suffix2"
    setProguardFiles(listOf("proguard-android-2.txt", "proguard-rules-2.txt"))
  }
}
