android.signingConfigs.getByName("release").storeFile = file("release.keystore")
android.signingConfigs.getByName("release").storePassword = "password"
android.signingConfigs.getByName("release").storeType = "type"
android.signingConfigs.getByName("release").keyAlias = "myReleaseKey"
android.signingConfigs.getByName("release").keyPassword = "releaseKeyPassword"
