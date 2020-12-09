android {
  signingConfigs {
    create("myConfig") {
      storeFile = file("config.keystore")
    }
    create("myBetterConfig") {
      storeFile = file("betterConfig.keystore")
    }
  }
  buildTypes {
    create("xyz") {
      signingConfig = signingConfigs.getByName("myConfig")
    }
  }
}