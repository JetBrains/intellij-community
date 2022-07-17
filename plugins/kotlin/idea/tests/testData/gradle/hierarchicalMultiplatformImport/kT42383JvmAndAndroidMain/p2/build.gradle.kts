plugins {
    kotlin("multiplatform")
    id("com.android.library")
}
android {
    compileSdkVersion({{compile_sdk_version}})
}
kotlin {
    js { browser() }
    jvm()
    android()
    sourceSets {
        val jvmAndJsMain = create("jvmAndJsMain") {
            dependsOn(getByName("commonMain"))
        }
        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(getByName("commonMain"))
        }
        getByName("jsMain") {
            dependsOn(jvmAndJsMain)
        }
        getByName("androidMain") {
            dependsOn(jvmAndAndroidMain)
        }
        getByName("jvmMain") {
            dependsOn(jvmAndJsMain)
            dependsOn(jvmAndAndroidMain)
        }
    }
}
