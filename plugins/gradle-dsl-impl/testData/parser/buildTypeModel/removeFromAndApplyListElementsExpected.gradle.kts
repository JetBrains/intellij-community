android {
  buildTypes {
    create("xyz") {
      buildConfigField("cdef", "ghij", "klmn")
      consumerProguardFiles("proguard-android.txt")
      proguardFiles("proguard-android.txt")
      resValue("mnop", "qrst", "uvwx")
    }
  }
}
