plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
    id("com.android.application")
    id("kotlin-android-extensions")
}

allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    android()
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}
