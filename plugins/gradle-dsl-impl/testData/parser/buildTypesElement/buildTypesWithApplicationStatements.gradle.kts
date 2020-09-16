android {
  buildTypes {
    create("type1") {
      setApplicationIdSuffix("suffix1")
      proguardFiles("proguard-android-1.txt", "proguard-rules-1.txt")
    }
    create("type2") {
      setApplicationIdSuffix("suffix2")
      proguardFiles("proguard-android-2.txt", "proguard-rules-2.txt")
    }
  }
}
