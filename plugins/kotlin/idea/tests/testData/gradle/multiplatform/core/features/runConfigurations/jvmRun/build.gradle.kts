plugins {
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    targetHierarchy.default()
    jvm()
    iosX64()
    iosArm64()
}
