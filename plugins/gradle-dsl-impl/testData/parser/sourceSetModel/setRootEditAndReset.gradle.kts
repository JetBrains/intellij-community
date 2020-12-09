android {
  sourceSets {
    create("set1") {
      setRoot("source1")
    }
    create("set2").setRoot("source2")
  }
  sourceSets.create("set3").setRoot("source3")
}
android.sourceSets.create("set4").setRoot("source4")
