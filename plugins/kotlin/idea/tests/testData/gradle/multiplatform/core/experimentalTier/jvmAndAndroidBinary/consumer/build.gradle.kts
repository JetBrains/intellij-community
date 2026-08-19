plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

kotlin {
    jvm()
    {{androidTargetPlaceholder}}

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:producer:1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
            }
        }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
            {{android_main_kotlin_source_dirs}}
        }

        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)
        }
    }
}
