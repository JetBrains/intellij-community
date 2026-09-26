plugins {
    java
    kotlin("jvm") version "{{kgp_version}}"
    kotlin("kapt") version "{{kgp_version}}"
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}
