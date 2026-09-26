plugins {
    kotlin("multiplatform")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.google.guava:guava:32.1.0-jre")
            }
        }
        val commonTest by getting
        val jvmMain by getting
        val jvmTest by getting
    }
}