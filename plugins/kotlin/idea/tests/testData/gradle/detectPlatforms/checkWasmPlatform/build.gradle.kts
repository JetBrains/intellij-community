plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    wasm {
        binaries.executable()
        browser {}
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val wasmTest by getting {
            dependencies {
                implementation(kotlin("test-wasm"))
                implementation(kotlin("test-annotations-wasm"))
            }
        }
    }
}
