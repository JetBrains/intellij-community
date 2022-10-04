plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    macosX64()

    sourceSets.getByName("commonMain").dependencies {
        implementation(project(":b"))
    }
}
