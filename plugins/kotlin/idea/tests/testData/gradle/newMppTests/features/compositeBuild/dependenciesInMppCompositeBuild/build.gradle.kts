plugins {
    kotlin("multiplatform") version "{{kgp_version}}"
}

allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

kotlin {
    ios()
    jvm()
}
