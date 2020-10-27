android {
  buildTypes {
    create("xyz") {
      setManifestPlaceholders(mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"))
    }
  }
}
