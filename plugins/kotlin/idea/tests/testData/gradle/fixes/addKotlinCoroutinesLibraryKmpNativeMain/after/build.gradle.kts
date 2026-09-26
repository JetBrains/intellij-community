plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    sourceSets.nativeMain.dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:{{coroutines_version}}")
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}