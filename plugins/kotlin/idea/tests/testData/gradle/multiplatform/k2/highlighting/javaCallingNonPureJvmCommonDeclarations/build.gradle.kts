plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm {}
    js {
        nodejs()
    }

    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }
}
