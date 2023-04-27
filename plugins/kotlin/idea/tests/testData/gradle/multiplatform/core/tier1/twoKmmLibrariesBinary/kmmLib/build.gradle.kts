plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}

kotlin {
    ios()
    jvm()
}
