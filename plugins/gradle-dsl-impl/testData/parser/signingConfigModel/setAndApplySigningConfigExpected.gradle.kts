android {
  signingConfigs {
    create("release") {
      storeFile = file("debug.keystore")
      storePassword = "debug_password"
      storeType = "debug_type"
      keyAlias = "myDebugKey"
      keyPassword = "debugKeyPassword"
    }
  }
}
