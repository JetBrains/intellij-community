buildscript {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    id("include-plugin.kotlin-conventions")
}