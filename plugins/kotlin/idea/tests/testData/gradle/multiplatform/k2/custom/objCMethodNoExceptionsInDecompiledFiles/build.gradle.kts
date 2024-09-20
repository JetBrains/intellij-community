plugins {
    kotlin("multiplatform")
}

allprojects {
    group = "a"
    version = "1.0"

    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

kotlin {
    iosArm64()
    iosX64()
}
