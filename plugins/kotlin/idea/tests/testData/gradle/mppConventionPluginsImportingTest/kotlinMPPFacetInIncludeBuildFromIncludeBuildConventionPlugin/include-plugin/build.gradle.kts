plugins {
    `kotlin-dsl`
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:{{kgp_version}}")
}
