android {
  signingConfigs {
    create("myConfig") {
      storeFile = file("my_file.txt")
      storePassword = System.getenv("KSTOREPWD")
    }
  }
}

val prop1 by extra("value")
val prop2 by extra(25)
val prop3 by extra(true)
val prop4 by extra(mapOf("key" to "val"))
val prop5 by extra(listOf("val1", "val2", "val3"))
val prop6 by extra(25.3)
