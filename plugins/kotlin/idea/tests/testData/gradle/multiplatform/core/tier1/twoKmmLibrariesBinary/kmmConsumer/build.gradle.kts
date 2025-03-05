plugins {
    kotlin("multiplatform")
}

kotlin {
    {{iosTargetPlaceHolder}}
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin.mpp.tests:kmmLib:1.0")
            }
        }
    }
}
