plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm {

    }

    sourceSets.find {
        it.name == "jvmMain"
    }?.run {
        kotlin.setSrcDirs(listOf("customJvmName/nonSrc/kotlinPlus"))
    }
}