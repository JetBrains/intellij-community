plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":producer"))
            }
        }
    }
}
