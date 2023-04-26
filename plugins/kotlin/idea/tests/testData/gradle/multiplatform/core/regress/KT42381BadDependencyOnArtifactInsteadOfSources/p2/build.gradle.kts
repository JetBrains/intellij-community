plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    js() // arbitrary
    jvm()
    android()
}
