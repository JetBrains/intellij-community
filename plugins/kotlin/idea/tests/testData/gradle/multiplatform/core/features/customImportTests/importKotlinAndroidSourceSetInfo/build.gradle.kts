plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    jvm()
    linuxX64()
}
