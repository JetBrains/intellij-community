android {
  productFlavors {
    create("flavor1") {
      setApplicationId("com.example.myFlavor1")
      proguardFiles("proguard-android-1.txt", "proguard-rules-1.txt")
      testInstrumentationRunnerArguments(mapOf("key1" to "value1", "key2" to "value2"))
    }
    create("flavor2") {
      applicationId = "com.example.myFlavor2"
      proguardFiles("proguard-android-2.txt", "proguard-rules-2.txt")
      testInstrumentationRunnerArguments(mapOf("key3" to "value3", "key4" to "value4"))
    }
  }
  productFlavors.getByName("flavor1") {
    applicationId = "com.example.myFlavor1-1"
  }
  productFlavors.getByName("flavor2") {
    setProguardFiles(listOf("proguard-android-4.txt", "proguard-rules-4.txt"))
  }
  productFlavors {
    getByName("flavor1").testInstrumentationRunnerArguments = mutableMapOf("key5" to "value5", "key6" to "value6")
    getByName("flavor2").applicationId = "com.example.myFlavor2-1"
  }
}
android.productFlavors.getByName("flavor1").setProguardFiles(listOf("proguard-android-3.txt", "proguard-rules-3.txt"))
android.productFlavors.getByName("flavor2").testInstrumentationRunnerArguments = mutableMapOf("key7" to "value7", "key8" to "value8")
