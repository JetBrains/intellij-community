plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR)
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:producer:1.0")
            }
        }
    }
}
