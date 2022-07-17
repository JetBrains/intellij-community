plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
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

