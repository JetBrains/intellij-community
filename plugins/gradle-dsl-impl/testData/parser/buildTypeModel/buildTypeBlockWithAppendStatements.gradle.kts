android {
  buildTypes {
    create("xyz") {
      buildConfigField("abcd", "efgh", "ijkl")
      proguardFiles("pro-1.txt", "pro-2.txt")
      resValue("mnop", "qrst", "uvwx")
    }
  }
}
android.buildTypes.getByName("xyz") {
  buildConfigField("cdef", "ghij", "klmn")
  manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
  proguardFile("pro-3.txt")
  resValue("opqr", "stuv", "wxyz")
}

android.buildTypes.getByName("xyz").manifestPlaceholders["activityLabel3"] = "defaultName3"
android.buildTypes.getByName("xyz").manifestPlaceholders["activityLabel4"] = "defaultName4"
android.buildTypes.getByName("xyz").proguardFiles("pro-4.txt", "pro-5.txt")
