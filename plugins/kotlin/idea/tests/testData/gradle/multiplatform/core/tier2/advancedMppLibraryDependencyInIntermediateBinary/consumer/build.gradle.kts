plugins {
    kotlin("multiplatform")
}

kotlin {
    js()
    jvm()
    linuxX64()
    macosArm64()
    iosArm64()
    iosX64()

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

            val macosArm64 = getByName("macosArm64$classifier") {
                dependsOn(apple)
            }

            val iosArm64 = getByName("iosArm64$classifier") {
                dependsOn(apple)
            }

            val iosX64 = getByName("iosX64$classifier") {
                dependsOn(apple)
            }
        }

        // Dependencies
        // No dependencies on producer expected in commonMain/jvmAndNativeMain
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:producer:1.0")
            }
        }

        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:producer:1.0")
            }
        }
    }
}
