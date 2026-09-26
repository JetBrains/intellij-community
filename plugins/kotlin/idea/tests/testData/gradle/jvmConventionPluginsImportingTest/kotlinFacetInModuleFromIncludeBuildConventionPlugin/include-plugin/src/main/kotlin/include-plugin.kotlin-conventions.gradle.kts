plugins {
    kotlin("jvm")
}

java.targetCompatibility = JavaVersion.VERSION_11

kotlin {
    compilerOptions {
        apiVersion.set({{minimalSupportedKotlinVersion}})
        languageVersion.set({{minimalSupportedKotlinVersion}})
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation(kotlin("stdlib"))
}
