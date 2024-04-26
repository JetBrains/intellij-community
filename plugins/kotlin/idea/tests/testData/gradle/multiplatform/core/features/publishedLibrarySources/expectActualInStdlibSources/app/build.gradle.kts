plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js()
    linuxX64()
    iosArm64()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}