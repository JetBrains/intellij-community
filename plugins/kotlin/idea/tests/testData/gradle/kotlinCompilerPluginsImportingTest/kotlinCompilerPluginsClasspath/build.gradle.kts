plugins {
    kotlin("jvm") version "{{kgp_version}}"
    kotlin("plugin.allopen") version "{{kgp_version}}"
    kotlin("plugin.serialization") version "{{kgp_version}}"
    kotlin("plugin.lombok") version "{{kgp_version}}"
    kotlin("plugin.noarg") version "{{kgp_version}}"
    kotlin("plugin.jpa") version "{{kgp_version}}"

    // FIXME it seems that this plugin doesn't work in Gradle Kotlin DSL for some reason
    //kotlin("plugin.sam.with.receiver") version "{{kgp_version}}"
}

allOpen {
    annotation("com.my.Annotation")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}
