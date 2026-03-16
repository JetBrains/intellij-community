plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    {{iosTargetPlaceholder}}
    linuxX64()
    linuxArm64()

    val commonMain by sourceSets.getting
    val nativeMain by sourceSets.creating
    val linuxMain by sourceSets.creating
    val linuxX64Main by sourceSets.getting
    val linuxArm64Main by sourceSets.getting

    nativeMain.dependsOn(commonMain)
    linuxMain.dependsOn(nativeMain)
    iosMain.dependsOn(nativeMain)

    linuxX64Main.dependsOn(linuxMain)
    linuxArm64Main.dependsOn(linuxMain)
}