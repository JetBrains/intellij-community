plugins {
    kotlin("multiplatform")
}

kotlin {
    linuxX64()
    iosArm64()
        sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared"))
            }
        }
    }
}
