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
    val jsMain by sourceSets.getting

    sourceSets {
        val includedIntoJvm by creating { }
        val includedIntoJvmAndJs by creating { }

        jvmMain.dependsOn(includedIntoJvm)

        jvmMain.dependsOn(includedIntoJvmAndJs)
        jsMain.dependsOn(includedIntoJvmAndJs)
    }
}
