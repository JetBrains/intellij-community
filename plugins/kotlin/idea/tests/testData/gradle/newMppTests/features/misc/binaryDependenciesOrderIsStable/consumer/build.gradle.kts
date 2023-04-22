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
                // NB: intentionally declaring dependencies in an order that
                // doesn't correspond to lexicographical order
                implementation("org.jetbrains.kotlin.mpp.tests:lib2:1.0")
                implementation("org.jetbrains.kotlin.mpp.tests:lib1:1.0")
            }
        }
    }
}
