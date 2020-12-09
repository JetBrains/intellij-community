android {
    buildTypes {
        getByName("release") {
            // Proguard is used to shrink our apk, and reduce the number of methods in our final apk,
            // but we don't obfuscate the bytecode.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile('proguard-android.txt'), 'proguard.cfg')
            applicationIdSuffix = "releaseSuffix"
        }

        getByName("debug") {
            isMinifyEnabled = false
            buildConfigField("String", "APP_PN_KEY", "\"com.test\"")
            applicationIdSuffix = "debugSuffix"
        }
    }
}

android.buildTypes.all {
  isMinifyEnabled = false
  applicationIdSuffix = "override"
}
