android {
  signingConfigs {
    create("release") {
      storeFile = file("release.keystore")
      storePassword = "storePassword"
      storeType = "PKCS12"
      keyAlias = "myReleaseKey"
      keyPassword = "keyPassword"
    }
  }
  defaultConfig {
    signingConfig = signingConfigs.getByName("release")
    // all of the following are terrible ideas
    multiDexKeepFile = signingConfigs.getByName("release").storeFile
    applicationIdSuffix = signingConfigs.getByName("release").storePassword
    testInstrumentationRunner = signingConfigs.getByName("release").storeType
    testApplicationId = signingConfigs.getByName("release").keyAlias
    versionName = signingConfigs.getByName("release").keyPassword
  }
}
