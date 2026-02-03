group = "org.jetbrains"
version = "1.0"

plugins {
    kotlin("multiplatform")
}

kotlin {

    linuxX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations["main"].cinterops.create("myInterop") {
            defFile(isolated.projectDirectory.file("src/nativeInterop/cinterop/myInterop.def"))
            includeDirs(isolated.projectDirectory.file("../libs/include"))
        }
    }
}





