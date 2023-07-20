plugins {
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()

    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    }

    sourceSets.commonTest.get().dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
    }

    sourceSets.getByName("jvmTest").dependencies {
        implementation(kotlin("test-junit"))
    }
}
