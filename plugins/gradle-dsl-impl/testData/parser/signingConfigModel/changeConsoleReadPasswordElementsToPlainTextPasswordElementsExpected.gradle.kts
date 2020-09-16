android {
  signingConfigs {
    create("release") {
      storePassword = "store_password"
      keyPassword = "key_password"
    }
  }
}
