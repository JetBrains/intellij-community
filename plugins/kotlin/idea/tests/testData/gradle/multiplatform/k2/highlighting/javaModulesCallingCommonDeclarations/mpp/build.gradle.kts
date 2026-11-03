plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
    }
}
