android {
  sourceSets {
    create("set1") {
      setRoot("source1")
      setRoot("override1")
    }
    create("set2") {
      setRoot("source2")
    }
    getByName("set2").setRoot("override2")
    create("set3") {
      setRoot("source3")
    }
    create("set4") {
      setRoot("source4")
    }
  }
  sourceSets.getByName("set3").setRoot("override3")
}
android.sourceSets.getByName("set4").setRoot("override4")
