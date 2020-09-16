android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2")
    }
  }
}
