@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
    {{android_application_compatible_plugin_id}}
}

{{default_android_block}}

kotlin {
    jvm()
    {{androidTargetPlaceholder}}

    sourceSets.commonMain.get().dependencies {
        implementation("org.jetbrains.sample:producerA:1.0.0-SNAPSHOT")
    }
}
