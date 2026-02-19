plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.example.kmpDependsOnAndroidKMPLibrary"
        compileSdk = 35
        minSdk = 26
    }

    val xcfName = "shared"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }
}

