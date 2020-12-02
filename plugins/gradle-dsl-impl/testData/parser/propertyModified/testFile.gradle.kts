android {
  defaultConfig {
    proguardFiles("proguard-android-1.txt", "proguard-rules-1.txt")
    testInstrumentationRunnerArguments = mutableMapOf("key1" to "value1", "key2" to "value2")
  }
  signingConfigs {
    create("myConfig") {
      storeFile = file("my_file.txt")
      storePassword = System.getenv("KSTOREPWD")
    }
  }
}

var prop1 by extra("value")
var prop2 by extra(25)
var prop3 by extra(prop2)
var prop4 by extra(mapOf("key" to "val"))
var prop5 by extra(listOf("val1", "val2", "val3"))
var prop6 by extra(25.3)

dependencies {
  testCompile("some:gradle:thing", "some:other:gradle:thing")
}
