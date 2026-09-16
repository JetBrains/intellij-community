plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
}

tasks.register("unrelatedTask") {
    error("KTIJ-39752: this task must not be realized during Gradle sync")
}
