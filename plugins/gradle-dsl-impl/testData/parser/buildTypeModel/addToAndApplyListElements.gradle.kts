android {
  buildTypes {
    create("xyz") {
      buildConfigField("abcd", "efgh", "ijkl")
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      resValue("mnop", "qrst", "uvwx")
    }
  }
}