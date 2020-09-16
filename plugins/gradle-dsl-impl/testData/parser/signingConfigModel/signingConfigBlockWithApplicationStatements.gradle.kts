android {
  signingConfigs {
    create("release") {
      setStoreFile(file("release.keystore"))
      setStorePassword("password")
      setStoreType("type")
      setKeyAlias("myReleaseKey")
      setKeyPassword("releaseKeyPassword")
    }
  }
}
