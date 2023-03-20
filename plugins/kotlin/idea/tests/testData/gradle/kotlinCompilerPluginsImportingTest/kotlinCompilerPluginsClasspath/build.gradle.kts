plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
    kotlin("plugin.allopen") version "{{kotlin_plugin_version}}"
    kotlin("plugin.serialization") version "{{kotlin_plugin_version}}"
    kotlin("plugin.lombok") version "{{kotlin_plugin_version}}"
    kotlin("plugin.noarg") version "{{kotlin_plugin_version}}"
    kotlin("plugin.jpa") version "{{kotlin_plugin_version}}"

    // FIXME it seems that this plugin doesn't work in Gradle Kotlin DSL for some reason
    //kotlin("plugin.sam.with.receiver") version "{{kotlin_plugin_version}}"
}

allOpen {
    annotation("com.my.Annotation")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}
