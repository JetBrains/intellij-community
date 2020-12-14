android {
  signingConfigs {
    create("release") {
      storePassword = System.console().readLine("Keystore password: ")
      keyPassword = System.console().readLine("Key password: ")
    }
  }
}