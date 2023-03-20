import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    ios()
    linuxX64()
    linuxArm64()

    val commonMain by sourceSets.getting
    val linuxMain by sourceSets.creating
    val linuxX64Main by sourceSets.getting
    val linuxArm64Main by sourceSets.getting

    linuxMain.dependsOn(commonMain)
    linuxX64Main.dependsOn(linuxMain)
    linuxArm64Main.dependsOn(linuxMain)

    commonMain.dependencies {
        implementation(project(":p1"))
    }
}
