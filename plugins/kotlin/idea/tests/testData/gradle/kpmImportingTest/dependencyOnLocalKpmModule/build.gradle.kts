import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.*

plugins {
    kotlin("{{kpm_plugin_name}}").apply(false)
}
allprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.{{kpm_plugin_name}}") {
        configure<KotlinPm20ProjectExtension> {
            mainAndTest {
                jvm
                val linuxX64 by fragments.creating(GradleKpmLinuxX64Variant::class)
                val iosArm64 by fragments.creating(GradleKpmIosArm64Variant::class)
                val iosX64 by fragments.creating(GradleKpmIosX64Variant::class)

                val ios by fragments.creating {
                    iosArm64.refines(this)
                    iosX64.refines(this)
                }

                val jvmAndLinux by fragments.creating {
                    jvm.refines(this)
                    linuxX64.refines(this)
                }

                val native by fragments.creating {
                    linuxX64.refines(this)
                    ios.refines(this)
                }
            }
        }
    }
}

group = "project"
version = "1.0"
