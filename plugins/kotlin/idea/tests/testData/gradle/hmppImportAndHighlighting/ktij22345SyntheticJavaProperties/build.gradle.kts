plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm {
        withJava()
    }
}
