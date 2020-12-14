android {
  buildTypes {
    create("xyz") {
      consumerProguardFiles("proguard-android.txt")
      setProguardFiles(listOf("proguard-rules.pro"))
    }
  }
}
