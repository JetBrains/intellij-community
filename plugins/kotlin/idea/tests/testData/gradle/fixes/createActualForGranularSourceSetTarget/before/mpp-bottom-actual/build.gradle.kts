plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

kotlin {
    jvm()
    js {
        browser()
    }
    iosX64()
    sourceSets {
        val commonMain by getting {
            dependencies {
                kotlin("stdlib-common")
                kotlin("stdlib")
            }
        }

        val jvmMain by getting {
        }

        val jsMain by getting {
        }

        val jsJvm18Main by creating {
            dependsOn(commonMain)
            dependsOn(jsMain)
            dependsOn(jvmMain)
        }

        val iosX64Main by getting {
            dependsOn(commonMain)
        }
    }
}