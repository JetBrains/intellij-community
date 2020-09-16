android {
  buildTypes {
    getByName("release") {
      isJniDebuggable = android.buildTypes.getByName("debug").isDebuggable
    }
  }
}
