android {
  buildTypes {
    create("xyz") {
      buildConfigField("abcd", "efgh", "ijkl")
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt")
      proguardFiles("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt")
      resValue("mnop", "qrst", "uvwx")
      buildConfigField("cdef", "ghij", "klmn")
      resValue("opqr", "stuv", "wxyz")
    }
  }
}
