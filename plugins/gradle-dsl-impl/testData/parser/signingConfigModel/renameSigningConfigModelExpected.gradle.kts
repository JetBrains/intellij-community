android {
  signingConfigs {
    create("newName") {
      setStoreFile(file("release.keystore"))
      setStorePassword("password")
      setStoreType("type")
      setKeyAlias("myReleaseKey")
      setKeyPassword("releaseKeyPassword")
    }
  }
}
