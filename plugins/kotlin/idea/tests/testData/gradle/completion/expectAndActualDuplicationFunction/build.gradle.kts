plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm()
    js {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                kotlin("stdlib-common")
                kotlin("stdlib")
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain)
        }

        val jsMain by getting {
        }

        val jsJvm18Main by creating {
            dependsOn(commonMain)
            dependsOn(jsMain)
            dependsOn(jvmMain)
        }
    }
}
