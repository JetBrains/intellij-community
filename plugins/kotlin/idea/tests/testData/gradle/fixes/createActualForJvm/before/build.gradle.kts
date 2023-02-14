plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

group = "me.one.two"
version = "1.0-SNAPSHOT"

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm {
        withJava()
    }
    js {}
}
