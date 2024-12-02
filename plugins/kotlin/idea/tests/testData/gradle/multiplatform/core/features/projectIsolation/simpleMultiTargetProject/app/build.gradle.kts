plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm() {
        withJava()
    }
    macosArm64()

    sourceSets.nativeMain.dependencies {
        implementation(project(":lib"))
    }
}
