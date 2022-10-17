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
        val jsMain by getting
        val commonTest by getting
        val jvmTest by getting
        val linuxTest by getting
        val jsTest by getting

        create("webMain") {
            dependsOn(commonMain)
            jvmMain.dependsOn(this)
            jsMain.dependsOn(this)
        }

        create("concurrentMain") {
            dependsOn(commonMain)
            jvmMain.dependsOn(this)
            linuxMain.dependsOn(this)
        }
    }
}
