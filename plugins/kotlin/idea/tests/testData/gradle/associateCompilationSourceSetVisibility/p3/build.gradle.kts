plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR)
    linuxX64("linux")

    sourceSets {
        val commonMain by getting
        val jvmMain by getting
        val linuxMain by getting
        val commonTest by getting
        val jvmTest by getting
        val linuxTest by getting

        create("concurrentMain") {
            dependsOn(commonMain)
            jvmMain.dependsOn(this)
            linuxMain.dependsOn(this)
        }
    }
}
