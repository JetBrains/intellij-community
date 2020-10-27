android {
  signingConfigs {
    create("myConfig") {
      storeFile = file("config.keystore")
    }
  }
  buildTypes {
    create("xyz") {
      signingConfig = signingConfigs.getByName("myConfig")
    }
  }
}
