plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
}

dependencies {
    testImplementation(project(path = ":JavaOnly", configuration = "default"))
}