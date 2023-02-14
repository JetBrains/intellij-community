plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()

    val commonMain by sourceSets.getting
    val commonTest by sourceSets.getting
    val linuxX64Main by sourceSets.getting
    val linuxX64Test by sourceSets.getting
    val linuxArm64Main by sourceSets.getting
    val linuxArm64Test by sourceSets.getting

    val nativeMain by sourceSets.creating
    val nativeTest by sourceSets.creating

    nativeMain.dependsOn(commonMain)
    nativeTest.dependsOn(commonTest)

    linuxX64Main.dependsOn(nativeMain)
    linuxArm64Main.dependsOn(nativeMain)

    linuxX64Test.dependsOn(nativeTest)
    linuxArm64Test.dependsOn(nativeTest)

    commonTest.dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
    }
}
