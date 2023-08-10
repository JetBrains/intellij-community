plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}


group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}

kotlin {
    jvm()
    linuxX64()
}
