android {
  buildTypes {
    getByName("debug") {
      isDebuggable = true
    }
    getByName("release") {
      isJniDebuggable = android.buildTypes.getByName("debug").isDebuggable
    }
  }
}
