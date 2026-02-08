plugins {
    kotlin("multiplatform")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    jvm()
    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    macosArm64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
