android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mutableMapOf("activityLabel2" to "defaultName2")
    }
  }
}
