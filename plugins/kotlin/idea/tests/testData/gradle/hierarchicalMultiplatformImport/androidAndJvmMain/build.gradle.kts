
repositories {
    {{kts_kotlin_plugin_repositories}}
}

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
}

kotlin {
    android()
    jvm()

    sourceSets {
        val commonMain = getByName("commonMain")

        val androidAndJvmMain = create("androidAndJvmMain").apply {
            dependsOn(commonMain)
        }

        val androidMain = getByName("androidMain").apply {
            dependsOn(androidAndJvmMain)
        }

        val jvmMain = getByName("jvmMain").apply {
            dependsOn(androidAndJvmMain)
        }
    }
}

