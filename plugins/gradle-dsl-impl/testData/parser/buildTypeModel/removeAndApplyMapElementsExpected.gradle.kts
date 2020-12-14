android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mapOf("activityLabel2" to "defaultName2")
    }
  }
}
