android {
  buildTypes {
    create("xyz") {
    }
    getByName("release") {
      isJniDebuggable = android.buildTypes.getByName("xyz").isDebuggable
    }
  }
}
