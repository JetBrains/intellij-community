plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.example.dumblib:lib:0.0.1")
            }
        }
    }
}