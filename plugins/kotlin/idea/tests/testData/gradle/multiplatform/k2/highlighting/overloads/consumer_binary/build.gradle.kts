plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR)

    sourceSets.commonMain.dependencies {
        implementation("a:producer:1.0")
    }
}
