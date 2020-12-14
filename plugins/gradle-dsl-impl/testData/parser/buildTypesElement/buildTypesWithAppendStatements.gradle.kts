android {
  buildTypes {
    create("type1") {
      setProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.txt"))
    }
    create("type2") {
      proguardFiles("proguard-android-2.txt", "proguard-rules-2.txt")
    }
  }
  buildTypes.getByName("type1") {
    proguardFiles("proguard-android-3.txt", "proguard-rules-3.txt")
  }
  buildTypes.getByName("type2") {
  }
  buildTypes {
    getByName("type2").proguardFile("proguard-android-4.txt")
  }
}
