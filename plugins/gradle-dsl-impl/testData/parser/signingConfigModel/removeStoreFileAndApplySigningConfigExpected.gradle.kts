android {
  signingConfigs {
    create("release") {
      storePassword = "password"
      storeType = "type"
      keyAlias = "myReleaseKey"
      keyPassword = "releaseKeyPassword"
    }
  }
}
