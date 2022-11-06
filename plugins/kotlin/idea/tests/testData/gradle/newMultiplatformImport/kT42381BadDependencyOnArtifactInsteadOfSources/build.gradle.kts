plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}" apply false
}

buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.3")
    }
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

