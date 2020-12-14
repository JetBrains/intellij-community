android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
    }
  }
}
