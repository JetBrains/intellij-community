plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

dependencies {
    implementation("org.utils:api:0.7.0")
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.utils:api:0.7.0"))
            .using(module("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0"))
    }
}