plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    val commonMain by sourceSets.getting
    commonMain.dependencies {
        implementation(kotlin("stdlib"))
        implementation(project(":multiplatformAndroidJvmIosLibrary"))
        implementation("com.squareup.okio:okio:3.2.0")
    }
}