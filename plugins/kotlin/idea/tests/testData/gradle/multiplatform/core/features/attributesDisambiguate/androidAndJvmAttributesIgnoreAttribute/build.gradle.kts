allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("multiplatform").version("{{kgp_version}}") apply false
    kotlin("android").version("{{kgp_version}}") apply false
    {{android_library_plugin_id}}.version("{{agp_version}}") apply false
}
