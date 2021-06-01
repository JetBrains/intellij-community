import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    //kotlin("multiplatform") version "1.5.255-SNAPSHOT"
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}


allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        configure<KotlinMultiplatformExtension> {
            jvm()
        }
    }
}