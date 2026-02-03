buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

plugins {
    kotlin("multiplatform").version("{{kgp_version}}")
}

group = "project"
version = "1.0"

kotlin {
    jvm() 
    js(IR)

    val jvmMain by sourceSets.getting

    sourceSets {
        val pseudoOrphan by creating {
        }

        jvmMain.dependsOn(pseudoOrphan)
    }
}
