plugins {
    kotlin("multiplatform")
}

kotlin {
    {{iosTargetPlaceholder}}
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kmmLib"))
            }
        }
    }
}
