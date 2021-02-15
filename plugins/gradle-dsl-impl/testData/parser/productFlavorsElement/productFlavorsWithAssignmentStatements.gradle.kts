android.productFlavors {
  create("flavor1") {
    applicationId = "com.example.myFlavor1"
    setProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.txt"))
    testInstrumentationRunnerArguments = mutableMapOf("key1" to "value1", "key2" to "value2")
  }
  create("flavor2") {
    applicationId = "com.example.myFlavor2"
    setProguardFiles(listOf("proguard-android-2.txt", "proguard-rules-2.txt"))
    testInstrumentationRunnerArguments = mutableMapOf("key3" to "value3", "key4" to "value4")
  }
}
