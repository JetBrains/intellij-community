plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
}

subprojects {
    buildscript {
        repositories {
            { { kts_kotlin_plugin_repositories } }
        }
    }
}
