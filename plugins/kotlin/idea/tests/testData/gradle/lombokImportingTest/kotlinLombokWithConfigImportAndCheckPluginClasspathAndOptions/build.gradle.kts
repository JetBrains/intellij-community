plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
    kotlin("plugin.lombok") version "{{kotlin_plugin_version}}"
}

kotlinLombok {
    lombokConfigurationFile(file("lombok.config"))
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}