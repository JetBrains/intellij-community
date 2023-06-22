plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":multiplatformAndroidJvmIosLibrary"))
    implementation(project(":multiplatformJvmLibrary"))
}