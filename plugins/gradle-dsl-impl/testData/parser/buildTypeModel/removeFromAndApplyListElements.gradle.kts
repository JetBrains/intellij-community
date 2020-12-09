android {
  buildTypes {
    create("xyz") {
      buildConfigField("abcd", "efgh", "ijkl")
      buildConfigField("cdef", "ghij", "klmn")
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      resValue("mnop", "qrst", "uvwx")
      resValue("opqr", "stuv", "wxyz")
    }
  }
}