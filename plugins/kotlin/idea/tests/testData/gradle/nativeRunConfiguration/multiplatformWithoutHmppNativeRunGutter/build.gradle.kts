plugins {
    kotlin("multiplatform").version("{{kotlin_plugin_version}}")
}
repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    macosX64("macos") {
        binaries {
            executable {
                entryPoint = "sample.main"
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
    }
}