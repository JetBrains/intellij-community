plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    iosArm64()

    sourceSets.getByName("commonMain").dependencies {
        implementation(project(":libMpp"))
    }
}
