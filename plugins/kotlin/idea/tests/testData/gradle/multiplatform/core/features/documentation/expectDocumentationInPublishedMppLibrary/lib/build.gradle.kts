plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "com.example.dumblib"
version = "0.0.1"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}

kotlin {
    { { target_hierarchy } }

    jvm()
    linuxX64()
    iosArm64()
}