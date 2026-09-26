plugins {
    kotlin("multiplatform")
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

kotlin {
    js(IR)
    jvm()
}
