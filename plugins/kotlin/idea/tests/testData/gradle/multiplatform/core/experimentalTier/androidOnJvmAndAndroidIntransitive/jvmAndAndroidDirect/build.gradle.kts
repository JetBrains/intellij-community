plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    jvm()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":jvmAndAndroidTransitive"))
            }
        }
    }
}
