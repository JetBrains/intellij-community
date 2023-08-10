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
                implementation(project(":lib2"))
                implementation(project(":lib1"))
            }
        }
    }
}
