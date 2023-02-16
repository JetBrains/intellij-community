plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    linuxX64()
    linuxArm64()

    val commonMain by sourceSets.getting
    val nativeMain by sourceSets.creating
    val linuxX64Main by sourceSets.getting
    val linuxArm64Main by sourceSets.getting

    nativeMain.dependsOn(commonMain)
    linuxX64Main.dependsOn(nativeMain)
    linuxArm64Main.dependsOn(nativeMain)
}
