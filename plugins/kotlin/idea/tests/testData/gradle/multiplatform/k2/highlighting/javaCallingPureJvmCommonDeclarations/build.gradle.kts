plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm {}

    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }
}
