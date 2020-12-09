android {
  sourceSets {
    getByName("main") {
      manifest {
        srcFile("otherSource.xml")
      }
    }
  }
}
