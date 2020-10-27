android {
  buildTypes {
    getByName("debug") {
      applicationIdSuffix = "-debug"
    }
    getByName("release") {
      applicationIdSuffix = "-release"
    }
  }
}
