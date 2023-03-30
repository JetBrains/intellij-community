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
    linuxX64("linux")

    sourceSets {
        val commonMain by getting {}

        val intermediateBetweenLinuxAndCommon by creating {
            dependsOn(commonMain)
        }

        val linuxMain by getting {
            dependsOn(intermediateBetweenLinuxAndCommon)
        }

        val jvmMain by getting {
            dependsOn(linuxMain)
        }
    }
}
