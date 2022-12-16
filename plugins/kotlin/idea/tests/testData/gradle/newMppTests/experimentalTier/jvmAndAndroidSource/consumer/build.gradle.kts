plugins {
    kotlin("multiplatform")
    id("com.android.library")
}
android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
}
kotlin {
    jvm()
    android()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":producer"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
            }
        }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
        }

        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)
        }
    }
}
