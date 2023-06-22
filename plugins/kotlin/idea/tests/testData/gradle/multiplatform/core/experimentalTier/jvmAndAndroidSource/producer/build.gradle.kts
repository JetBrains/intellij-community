plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    jvm()
    android()

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
