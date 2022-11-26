plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}" apply false
}

buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    dependencies {
        classpath("com.android.tools.build:gradle:{{android_gradle_plugin_version}}")
    }
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

