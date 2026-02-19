plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    macosArm64()

    sourceSets.nativeMain.dependencies {
        implementation(project(":lib"))
    }
}
