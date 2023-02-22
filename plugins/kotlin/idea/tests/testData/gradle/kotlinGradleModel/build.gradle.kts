allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
}