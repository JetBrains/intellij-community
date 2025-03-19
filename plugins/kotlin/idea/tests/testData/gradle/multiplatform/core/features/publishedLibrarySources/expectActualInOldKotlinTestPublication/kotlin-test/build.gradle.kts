plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "com.example"
version = "0.0.1"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}

kotlin {
    { { target_hierarchy } }

    jvm()
    js()

    metadata { compilations.all { kotlinOptions.freeCompilerArgs += "-Xallow-kotlin-package" } }
    targets.all { compilations.all { kotlinOptions.freeCompilerArgs += "-Xallow-kotlin-package" } }

    sourceSets {
        jvmMain {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
    }
}