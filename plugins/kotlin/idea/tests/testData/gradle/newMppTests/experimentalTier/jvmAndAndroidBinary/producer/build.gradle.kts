plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
}

{{default_android_block}}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}


kotlin {
    jvm()
    android {
        publishLibraryVariants("release", "debug")
    }

    sourceSets {
        val commonMain by getting { }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
        }

        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)
        }
    }
}
