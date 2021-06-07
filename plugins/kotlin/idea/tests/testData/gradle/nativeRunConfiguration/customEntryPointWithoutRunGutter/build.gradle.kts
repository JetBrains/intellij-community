plugins {
    id("org.jetbrains.kotlin.multiplatform") version ("{{kotlin_plugin_version}}")
}
repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    macosX64("macos") {
        binaries {
            executable {
                entryPoint = "sample.foo"
            }
        }
    }
    sourceSets {
        val macosMain by getting {}
    }
}