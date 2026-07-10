plugins {
    kotlin("jvm") version "{{kgp_version}}"
    kotlin("plugin.lombok") version "{{kgp_version}}"
}

kotlinLombok {
    lombokConfigurationFile(file("lombok.config"))
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}