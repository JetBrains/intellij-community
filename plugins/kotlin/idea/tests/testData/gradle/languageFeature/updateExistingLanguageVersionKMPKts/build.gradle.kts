plugins {
    kotlin("multiplatform") version "1.8.0"
}

repositories {
    mavenCentral()
}

group = "com.example"
version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
        }
        all {
            languageSettings {
                languageVersion = "1.8"
            }
        }
    }
}