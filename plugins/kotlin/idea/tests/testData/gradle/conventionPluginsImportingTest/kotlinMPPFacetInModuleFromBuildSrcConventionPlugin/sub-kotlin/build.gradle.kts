buildscript {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    id("project.kotlin-conventions")
}