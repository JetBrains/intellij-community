android {
  signingConfigs {
    create("my new release.") {
      storeFile = file("release.keystore")
      storePassword = "storePassword"
      storeType = "PKCS12"
      keyAlias = "myReleaseKey"
      keyPassword = "keyPassword"
    }
  }
  defaultConfig {
    signingConfig = signingConfigs.getByName("my new release.")
    // all of the following are terrible ideas
    multiDexKeepFile = signingConfigs.getByName("my new release.").storeFile
    applicationIdSuffix = signingConfigs.getByName("my new release.").storePassword
    testInstrumentationRunner = signingConfigs.getByName("my new release.").storeType
    testApplicationId = signingConfigs.getByName("my new release.").keyAlias
    versionName = signingConfigs.getByName("my new release.").keyPassword
  }
}
