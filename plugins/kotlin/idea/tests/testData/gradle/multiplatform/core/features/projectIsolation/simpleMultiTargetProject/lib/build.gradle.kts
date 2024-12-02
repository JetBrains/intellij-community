plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm() {
        withJava()
    }
    macosArm64()
    macosX64()
}
