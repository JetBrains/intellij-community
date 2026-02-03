plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":test-utils"))
            }
        }

        nativeTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
