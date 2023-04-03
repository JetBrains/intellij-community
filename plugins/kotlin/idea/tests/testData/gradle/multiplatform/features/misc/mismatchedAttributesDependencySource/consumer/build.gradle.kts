plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR)
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":producer"))
            }
        }
    }
}
