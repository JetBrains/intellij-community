plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
    id("org.jetbrains.kotlin.plugin.lombok") version "{{kotlin_plugin_version}}"
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
}
