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
    js()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("com.squareup.moshi:moshi:1.8.0")
                implementation("com.squareup.moshi:moshi-kotlin:1.8.0")
            }
        }
    }
}
