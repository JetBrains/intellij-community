plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        nodejs()
    }
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                if (file("$rootDir/repo").exists()) {
                    implementation("org.jetbrains.kotlin.mpp.tests:producer:1.0")
                }
            }
        }
    }
}
