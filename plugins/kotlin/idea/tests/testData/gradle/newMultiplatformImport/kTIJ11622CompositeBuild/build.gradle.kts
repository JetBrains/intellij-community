import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    //kotlin("multiplatform") version "1.5.255-SNAPSHOT"
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

allprojects {
    repositories {
        //mavenCentral()
        //mavenLocal()
        { { kts_kotlin_plugin_repositories } }
    }
}

kotlin {
    jvm()
}