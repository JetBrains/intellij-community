plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

// 2.0.21 is the last standard library in which 'kotlin.Enum' is not an 'expect'/'actual' class
// (the builtins were rewritten as 'expect'/'actual' in 2.1, KT-65526).
val oldStdlibVersion = "2.0.21"

// Declaring the dependency is not enough: the Kotlin Gradle plugin adds its own standard library
// and, for JS targets, 'kotlin-dom-api-compat', which depends on the standard library of the same
// version. Conflict resolution then picks the plugin's version instead. Pin them all down.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" &&
            (requested.name.startsWith("kotlin-stdlib") || requested.name == "kotlin-dom-api-compat")
        ) {
            useVersion(oldStdlibVersion)
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()
    js {
        nodejs()
    }
}
