plugins {
    kotlin("multiplatform")
}


kotlin {
    jvm()
    linuxX64()

    val commonMain by sourceSets.getting
    commonMain.dependencies {
        implementation(kotlin("stdlib-common"))
    }
}
