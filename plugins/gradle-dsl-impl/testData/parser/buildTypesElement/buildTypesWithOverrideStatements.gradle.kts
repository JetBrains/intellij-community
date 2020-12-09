android {
  buildTypes {
    create("type1") {
      applicationIdSuffix = "suffix1"
      proguardFiles("proguard-android-1.txt", "proguard-rules-1.txt")
    }
    create("type2") {
      applicationIdSuffix = "suffix2"
      proguardFiles("proguard-android-2.txt", "proguard-rules-2.txt")
    }
  }
  buildTypes.getByName("type1") {
    applicationIdSuffix = "suffix1-1"
  }
  buildTypes.getByName("type2") {
    // TODO(xof): in the Kotlin DSL, we can't assign to proguardFiles;
    // this test wants to override the current property (rather than
    // extend), which is in principle possible with setProguardFiles
    // but currently unsupported.
    // proguardFiles("proguard-android-4.txt", "proguard-rules-4.txt")
  }
  buildTypes {
    getByName("type2").applicationIdSuffix = "suffix2-1"
  }
}
// TODO(xof) see above
// android.buildTypes.getByName("type1").proguardFiles = listOf("proguard-android-3.txt", "proguard-rules-3.txt")
