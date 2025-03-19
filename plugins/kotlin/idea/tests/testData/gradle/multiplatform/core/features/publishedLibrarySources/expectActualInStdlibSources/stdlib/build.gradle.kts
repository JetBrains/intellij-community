plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "com.example.stdlib"
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
    linuxX64()
    iosArm64()

    metadata { compilations.all { kotlinOptions.freeCompilerArgs += "-Xallow-kotlin-package" } }
    targets.all { compilations.all { kotlinOptions.freeCompilerArgs += "-Xallow-kotlin-package" } }
}