plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()

    targetHierarchy.default()

    sourceSets.commonMain.get().dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    }
}
