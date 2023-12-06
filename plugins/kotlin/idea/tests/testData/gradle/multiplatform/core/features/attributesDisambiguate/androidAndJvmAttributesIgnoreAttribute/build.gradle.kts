allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("multiplatform").version("{{kgp_version}}") apply false
    kotlin("android").version("{{kgp_version}}") apply false
    id("com.android.library").version("{{agp_version}}") apply false
}
