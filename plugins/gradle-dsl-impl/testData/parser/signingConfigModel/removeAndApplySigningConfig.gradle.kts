android {
  signingConfigs {
    create("release") {
      storeFile = file("release.keystore")
      storePassword = "password"
      storeType = "type"
      keyAlias = "myReleaseKey"
      keyPassword = "releaseKeyPassword"
    }
  }
}