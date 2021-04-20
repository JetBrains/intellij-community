plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdkVersion(26)
    buildToolsVersion("28.0.3")
}

kotlin {
    android()
    jvm()
    sourceSets {
        val commonMain = getByName("commonMain")

        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain)
        }

        getByName("jvmMain") {
            dependsOn(jvmAndAndroidMain)
        }

        getByName("androidMain") {
            dependsOn(jvmAndAndroidMain)
        }
    }
}

