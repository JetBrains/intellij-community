plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    linuxX64()
    jvm()
    {{androidTargetPlaceholder}}

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:expectedEverywhere:1.0")
            }
        }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:expectedInJvmAndAndroid:1.0")
            }
        }

        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
        }

        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:expectedInJvmOnly:1.0")
            }
        }
    }
}
