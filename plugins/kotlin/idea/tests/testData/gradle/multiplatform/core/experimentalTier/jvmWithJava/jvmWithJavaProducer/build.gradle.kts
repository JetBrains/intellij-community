plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm { withJava() }
    ios()
}
