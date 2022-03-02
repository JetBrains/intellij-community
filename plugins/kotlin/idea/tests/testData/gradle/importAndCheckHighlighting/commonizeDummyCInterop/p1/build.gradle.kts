plugins {
    kotlin("multiplatform")
}

kotlin {
    val x64 = linuxX64("x64")
    val arm64 = linuxArm64("arm64")

    val commonMain by sourceSets.getting
    val nativeMain by sourceSets.creating
    val x64Main by sourceSets.getting
    val arm64Main by sourceSets.getting

    nativeMain.dependsOn(commonMain)
    x64Main.dependsOn(nativeMain)
    arm64Main.dependsOn(nativeMain)

    sourceSets.all {
        languageSettings.optIn("kotlin.RequiresOptIn")
    }

    x64.compilations.getByName("main").cinterops.create("dummy")
    arm64.compilations.getByName("main").cinterops.create("dummy")
}
