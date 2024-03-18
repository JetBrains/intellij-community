plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR)

    sourceSets.commonMain.dependencies {
        implementation(project(":producer"))
    }
}
