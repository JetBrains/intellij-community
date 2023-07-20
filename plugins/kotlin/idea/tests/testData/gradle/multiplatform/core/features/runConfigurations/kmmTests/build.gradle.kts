plugins {
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    targetHierarchy.default()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets.commonTest.get().dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
    }

    sourceSets.getByName("jvmTest").dependencies {
        implementation(kotlin("test-junit"))
    }
}
