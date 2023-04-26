plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdk = {{compile_sdk_version}}
    sourceSets.getByName("main").manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

kotlin {
    android()
    jvm()
    linuxX64()

    val commonMain by sourceSets.getting
    val androidMain by sourceSets.getting
    val jvmMain by sourceSets.getting
    val androidAndJvmMain by sourceSets.creating

    androidAndJvmMain.dependsOn(commonMain)
    jvmMain.dependsOn(androidAndJvmMain)
    androidMain.dependsOn(androidAndJvmMain)

    commonMain.dependencies {
        implementation(project(":multiplatformAndroidJvmIosLibrary"))
    }

    androidMain.dependencies {
        implementation(project(":multiplatformAndroidLibrary"))
    }

    jvmMain.dependencies {
        implementation(project(":multiplatformJvmLibrary"))
        implementation(project(":jvmLibrary"))
    }
}
