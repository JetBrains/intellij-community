plugins {
    alias(libs.plugins.kotlinMpp)
}

kotlin {
    jvm()
    linuxX64()
    mingwX64()
    macosX64()

    sourceSets {
        jvmMain {
            dependencies {
                api(project(":taverncore"))
            }
        }
        val jvmAndLinux by creating {
            dependsOn(commonMain.get())
        }
        linuxX64Main.get().dependsOn(jvmAndLinux)
        jvmMain.get().dependsOn(jvmAndLinux)
    }
}
