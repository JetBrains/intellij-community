plugins {
    `kotlin-dsl` // <1>
}

repositories {
    gradlePluginPortal() // <2>
}

dependencies {
        implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
}
