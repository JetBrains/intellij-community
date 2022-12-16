plugins {
    kotlin("multiplatform").version("{{kotlin_plugin_version}}")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

group = "project"
version = "1.0"

kotlin {
    jvm() 
    js()

    sourceSets {
        val orphan by creating { }
    }
}
