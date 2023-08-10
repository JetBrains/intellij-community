plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
    id("org.jetbrains.kotlin.plugin.lombok") version "{{kotlin_plugin_version}}"
}

kotlinLombok {
    lombokConfigurationFile(file("lombok.config"))
}
