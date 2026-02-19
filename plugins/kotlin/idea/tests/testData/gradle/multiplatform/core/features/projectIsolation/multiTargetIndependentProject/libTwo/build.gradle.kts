plugins {
    kotlin("multiplatform")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    jvm()
    mingwX64()
    macosArm64()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
