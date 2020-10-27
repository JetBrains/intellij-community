android {
  signingConfigs {
    create("my release.") {
      storeFile = file("release.keystore")
      storePassword = "storePassword"
      storeType = "PKCS12"
      keyAlias = "myReleaseKey"
      keyPassword = "keyPassword"
    }
  }
  defaultConfig {
    signingConfig = signingConfigs.getByName("my release.")
    // all of the following are terrible ideas
    multiDexKeepFile = signingConfigs.getByName("my release.").storeFile
    applicationIdSuffix = signingConfigs.getByName("my release.").storePassword
    testInstrumentationRunner = signingConfigs.getByName("my release.").storeType
    testApplicationId = signingConfigs.getByName("my release.").keyAlias
    versionName = signingConfigs.getByName("my release.").keyPassword
  }
}
