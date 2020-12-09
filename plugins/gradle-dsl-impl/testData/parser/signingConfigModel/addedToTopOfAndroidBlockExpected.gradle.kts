android {
    signingConfigs {
        create("release") {
            storeFile = file(keystorefile)
            storePassword = "123456"
            keyAlias = "demo"
            keyPassword = "123456"
        }
    }
    compileSdkVersion(28)
    defaultConfig {
        applicationId = "com.example.keystoreapps"
        minSdkVersion(15)
        targetSdkVersion(28)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
