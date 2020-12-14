android {
  signingConfigs {
    create("release") {
      storePassword = System.getenv("KSTOREPWD1")
      keyPassword = System.getenv("KEYPWD1")
    }
  }
}
