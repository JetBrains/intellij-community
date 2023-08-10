plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

kotlin {
    js(IR)
    jvm()
    linuxX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        listOf("Main", "Test").forEach { classifier ->
            val common = getByName("common$classifier")

            val jvmAndNative = create("jvmAndNative$classifier") {
                dependsOn(common)
            }

            val native = create("native$classifier") {
                dependsOn(jvmAndNative)
            }

            val apple = create("apple$classifier") {
                dependsOn(native)
            }

            val jvm = getByName("jvm$classifier") {
                dependsOn(jvmAndNative)
            }

            val linuxX64 = getByName("linuxX64$classifier") {
                dependsOn(native)
            }

            val macosX64 = getByName("macosArm64$classifier") {
                dependsOn(apple)
            }

            val iosArm64 = getByName("iosArm64$classifier") {
                dependsOn(apple)
            }

            val iosX64 = getByName("iosSimulatorArm64$classifier") {
                dependsOn(apple)
            }
        }
    }
}
