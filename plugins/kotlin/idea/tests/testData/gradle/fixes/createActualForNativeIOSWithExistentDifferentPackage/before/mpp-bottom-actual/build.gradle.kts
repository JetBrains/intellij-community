plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    iosX64()
    sourceSets {
        commonMain {
            dependencies {
                kotlin("stdlib-common")
                kotlin("stdlib")
            }
        }
    }
}