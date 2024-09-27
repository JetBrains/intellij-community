plugins {
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm()
    macosX64()
    macosArm64()
    linuxX64()
    linuxArm64()

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
