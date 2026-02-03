plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation("com.android.tools.build:gradle:3.6.2")
}