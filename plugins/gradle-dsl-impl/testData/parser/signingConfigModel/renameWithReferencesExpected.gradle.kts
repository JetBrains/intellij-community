android {
  signingConfigs {
    create("newRelease") {
      storeFile = file("release.keystore")
      storePassword = "storePassword"
      storeType = "PKCS12"
      keyAlias = "myReleaseKey"
      keyPassword = "keyPassword"
    }
  }
  defaultConfig {
    signingConfig = signingConfigs.getByName("newRelease")
    // all of the following are terrible ideas
    multiDexKeepFile = signingConfigs.getByName("newRelease").storeFile
    applicationIdSuffix = signingConfigs.getByName("newRelease").storePassword
    testInstrumentationRunner = signingConfigs.getByName("newRelease").storeType
    testApplicationId = signingConfigs.getByName("newRelease").keyAlias
    versionName = signingConfigs.getByName("newRelease").keyPassword
  }
}
