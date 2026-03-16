plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm { withJava() }
    {{iosTargetPlaceholder}}
}
