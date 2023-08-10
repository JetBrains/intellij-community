plugins {
    kotlin("multiplatform")
}

kotlin {
    ios()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:kmmLib:1.0")
            }
        }
    }
}
