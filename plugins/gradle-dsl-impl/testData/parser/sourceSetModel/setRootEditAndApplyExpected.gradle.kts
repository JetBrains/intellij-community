android {
  sourceSets {
    create("set1") {
      setRoot("newRoot1")
    }
    create("set2").setRoot("newRoot2")
  }
  sourceSets.create("set3").setRoot("newRoot3")
}
android.sourceSets.create("set4").setRoot("newRoot4")
