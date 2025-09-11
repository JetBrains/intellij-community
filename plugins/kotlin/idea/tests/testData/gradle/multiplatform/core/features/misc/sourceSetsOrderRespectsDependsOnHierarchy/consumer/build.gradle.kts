plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

// jsMain -> b -> c -> a -> commonMain
kotlin {
    jvm()
    js()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":producer"))
            }
        }
        val jsMain by getting
        val linuxX64Main by getting

        val a by creating {
            this.dependsOn(commonMain)
        }

        val c by creating {
            this.dependsOn(a)
        }

        val b by creating {
            this.dependsOn(c)
        }

        jsMain.dependsOn(b)
        linuxX64Main.dependsOn(b)
    }
}
