val TEST_STRINGS by extra(listOf("test1", "test2"))

android {
  defaultConfig {
    proguardFiles(extra["TEST_STRINGS"])
  }
}
