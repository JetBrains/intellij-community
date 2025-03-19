plugins {
    alias(libs.plugins.kotlinMpp)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":fateweaver"))
            }
        }
    }
}