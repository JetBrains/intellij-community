plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.example.lib:lib:0.0.1")
            }
        }
    }
}