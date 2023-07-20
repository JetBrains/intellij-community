plugins {
    kotlin("multiplatform")
}

kotlin {
    ios()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kmmLib"))
            }
        }
    }
}
