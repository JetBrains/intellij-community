plugins {
    `kotlin-dsl`
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:{{kotlin_plugin_version}}")
}