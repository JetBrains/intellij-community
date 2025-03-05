plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

dependencies {
    implementation(project(":b"))
}
