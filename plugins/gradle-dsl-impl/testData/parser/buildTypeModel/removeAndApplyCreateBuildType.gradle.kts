android {
  buildTypes {
    create("xyz") {
      isDebuggable = true
    }
    getByName("release") {
      isJniDebuggable = android.buildTypes.getByName("xyz").isDebuggable
    }
  }
}
