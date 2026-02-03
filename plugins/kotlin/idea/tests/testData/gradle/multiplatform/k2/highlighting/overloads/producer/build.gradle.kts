plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    jvm()
    js(IR)
}

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}