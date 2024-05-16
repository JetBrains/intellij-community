plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    { { kts_kotlin_plugin_repositories } }
    mavenLocal()
}

kotlin {
    wasmWasi {
        binaries.executable()
        nodejs {}
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val wasmWasiTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-wasi"))
            }
        }

    }

}
