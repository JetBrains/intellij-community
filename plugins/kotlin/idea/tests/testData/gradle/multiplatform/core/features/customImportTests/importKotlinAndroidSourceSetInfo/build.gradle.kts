plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    android()
    jvm()
    linuxX64()
}
