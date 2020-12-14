android {
  signingConfigs {
    create("myConfig") {
      storeFile = file("config.keystore")
    }
  }
}