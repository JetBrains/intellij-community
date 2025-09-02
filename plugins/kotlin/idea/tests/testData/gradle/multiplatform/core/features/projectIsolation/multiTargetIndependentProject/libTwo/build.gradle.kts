plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    mingwX64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting
        val jvmTest by getting

        val mingwX64Main by getting
        val mingwX64Test by getting
    }
}
