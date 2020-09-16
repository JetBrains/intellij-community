android {
  buildTypes {
    create("xyz") {
    }
  }
}
android.buildTypes.getByName("xyz").manifestPlaceholders["activityLabel1"] = "defaultName1"
android.buildTypes.getByName("xyz").manifestPlaceholders["activityLabel2"] = "defaultName2"
