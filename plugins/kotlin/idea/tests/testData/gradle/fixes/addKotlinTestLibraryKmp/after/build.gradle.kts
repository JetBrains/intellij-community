plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    sourceSets {
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}