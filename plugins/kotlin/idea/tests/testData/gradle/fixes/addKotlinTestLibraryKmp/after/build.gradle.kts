plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}