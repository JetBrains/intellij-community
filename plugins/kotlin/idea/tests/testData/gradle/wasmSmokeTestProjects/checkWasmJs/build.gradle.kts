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
    wasmJs {
        binaries.executable()
        browser {}
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }

    }

}
