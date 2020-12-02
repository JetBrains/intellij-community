android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
    }
  }
}
