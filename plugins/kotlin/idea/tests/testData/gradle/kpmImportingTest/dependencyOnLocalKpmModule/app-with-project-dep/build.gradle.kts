plugins {
    kotlin("{{kpm_plugin_name}}")
}

kotlin {
    main.dependencies {
        api(project(":lib"))
    }
}