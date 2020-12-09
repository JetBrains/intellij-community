android {
  signingConfigs {
    create("release") {
      storePassword = System.console().readLine("Another Keystore Password: ")
      keyPassword = System.console().readLine("Another Key Password: ")
    }
  }
}
