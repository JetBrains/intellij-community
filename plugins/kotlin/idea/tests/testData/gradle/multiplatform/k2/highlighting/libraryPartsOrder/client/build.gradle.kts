plugins {
    kotlin("multiplatform")
}

kotlin {
    linuxX64("lin1")
    linuxArm64("lin2")
    macosArm64("mac1") {
        binaries {
            executable()
        }
    }

    sourceSets.commonMain.dependencies {
        implementation("a:lib:1.0")
        implementation(project(":source"))
    }
}
