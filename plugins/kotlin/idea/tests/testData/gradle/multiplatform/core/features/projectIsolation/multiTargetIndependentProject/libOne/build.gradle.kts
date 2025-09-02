plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting
        val jvmTest by getting

        val linuxX64Main by getting
        val linuxX64Test by getting
    }
}
