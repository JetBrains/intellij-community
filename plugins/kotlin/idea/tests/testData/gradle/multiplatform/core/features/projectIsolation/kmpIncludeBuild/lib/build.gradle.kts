plugins {
    kotlin("multiplatform") version "{{kgp_version}}"
}

group = "com.example"
version = "1.0.0"

kotlin {
    jvm()
    iosX64()
    linuxX64()
    mingwX64()
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}
