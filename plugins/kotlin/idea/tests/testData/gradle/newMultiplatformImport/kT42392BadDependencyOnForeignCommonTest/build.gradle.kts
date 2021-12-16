plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}" apply false
    id("com.android.library") version "3.6.4" apply false
}

buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.4")
    }
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

