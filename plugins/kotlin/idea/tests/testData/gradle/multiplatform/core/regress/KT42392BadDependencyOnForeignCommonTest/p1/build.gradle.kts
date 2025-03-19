plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

{{default_android_block}}

kotlin {
    jvm()
    {{androidTargetPlaceholder}}
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":p2"))
            }
        }
    }
}
