android {
  productFlavors {
    create("flavor1") {
      setApplicationId("com.example.myFlavor1")
      proguardFiles("proguard-android-1.txt", "proguard-rules-1.txt")
      testInstrumentationRunnerArguments(mapOf("key1" to "value1", "key2" to "value2"))
    }
    create("flavor2") {
      setApplicationId("com.example.myFlavor2")
      proguardFiles("proguard-android-2.txt", "proguard-rules-2.txt")
      testInstrumentationRunnerArguments(mapOf("key3" to "value3", "key4" to "value4"))
    }
  }
}
