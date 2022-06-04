import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.*

buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

group = "project"
version = "1.0"

plugins {
    kotlin("{{kpm_plugin_name}}") version "{{kotlin_plugin_version}}"
}

kotlin {
    mainAndTest {
        jvm
        val linuxX64 by fragments.creating(KotlinLinuxX64Variant::class)
        val iosArm64 by fragments.creating(KotlinIosArm64Variant::class)
        val iosX64 by fragments.creating(KotlinIosX64Variant::class)

        val ios by fragments.creating {
            iosArm64.refines(this)
            iosX64.refines(this)
        }

        val jvmAndLinux by fragments.creating {
            jvm.refines(this)
            linuxX64.refines(this)
        }

        val native by fragments.creating {
            linuxX64.refines(this)
            ios.refines(this)
        }
    }
}