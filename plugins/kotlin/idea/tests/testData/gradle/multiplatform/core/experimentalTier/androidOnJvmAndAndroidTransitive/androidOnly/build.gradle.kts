plugins {
    id("com.android.library")
}

{{default_android_block}}

dependencies {
    implementation(project(":jvmAndAndroidDirect"))
}
