plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
}

kotlin {
    jvm()
    iosX64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.lib)
            }
        }
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

