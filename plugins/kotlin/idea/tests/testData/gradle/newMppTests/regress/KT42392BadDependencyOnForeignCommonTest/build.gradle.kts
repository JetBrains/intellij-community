plugins {
    kotlin("multiplatform") version "{{kgp_version}}" apply false
    id("com.android.library") version "{{agp_version}}" apply false
}

buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    dependencies {
        classpath("com.android.tools.build:gradle:{{agp_version}}")
    }
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}
