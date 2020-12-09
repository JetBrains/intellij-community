android {
  productFlavors {
    create("flavor1") {
      setProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.txt"))
      testInstrumentationRunnerArguments(mapOf("key1" to "value1", "key2" to "value2"))
    }
    create("flavor2") {
      proguardFiles("proguard-android-2.txt", "proguard-rules-2.txt")
      testInstrumentationRunnerArguments = mapOf("key3" to "value3", "key4" to "value4")
    }
  }
  productFlavors.getByName("flavor1") {
    proguardFiles("proguard-android-3.txt", "proguard-rules-3.txt")
  }
  productFlavors.getByName("flavor2") {
    testInstrumentationRunnerArguments["key6"] = "value6"
  }
  productFlavors {
    getByName("flavor2").proguardFile("proguard-android-4.txt")
  }
}
android.productFlavors.flavor1.testInstrumentationRunnerArguments["key5"] = "value5"
