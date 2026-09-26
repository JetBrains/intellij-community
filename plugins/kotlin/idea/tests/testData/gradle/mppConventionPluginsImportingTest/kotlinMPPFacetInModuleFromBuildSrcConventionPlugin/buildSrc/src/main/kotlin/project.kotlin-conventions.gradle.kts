plugins {
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm()
    linuxX64()
    macosX64()

    sourceSets {
        val commonMain by getting {
        }

        val commonTest by getting {
        }

        val jvmAndLinuxMain by creating {
            dependsOn(commonMain)
        }

        val jvmAndLinuxTest by creating {
            dependsOn(commonTest)
        }

        val jvmMain by getting {
            dependsOn(jvmAndLinuxMain)
        }

        val jvmTest by getting {
            dependsOn(jvmAndLinuxTest)
        }

        val linuxX64Main by getting {
            dependsOn(jvmAndLinuxMain)
        }

        val linuxX64Test by getting {
            dependsOn(jvmAndLinuxTest)
        }
    }
}
