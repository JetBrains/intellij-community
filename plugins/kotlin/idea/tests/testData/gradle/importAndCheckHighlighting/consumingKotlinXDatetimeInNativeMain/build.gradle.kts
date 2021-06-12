plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}
group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm()
    macosX64("macosX64")
    iosX64("iosX64")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.1.1")
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        macosX64().compilations["main"].defaultSourceSet {
            dependsOn(nativeMain)
        }
        iosX64().compilations["main"].defaultSourceSet {
            dependsOn(nativeMain)
        }
    }
}