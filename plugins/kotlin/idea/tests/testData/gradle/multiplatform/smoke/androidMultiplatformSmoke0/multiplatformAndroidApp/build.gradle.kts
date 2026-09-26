plugins {
    {{android_library_plugin_id}}
    kotlin("multiplatform")
}

{{android_library_configuration_open}}
    compileSdk = {{compile_sdk_version}}
    namespace = "org.jetbrains.kotlin.smoke.multiplatformAndroidApp"
{{android_library_configuration_close}}

kotlin {
    {{android_target}}

    val commonMain by sourceSets.getting

    commonMain.dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation("androidx.appcompat:appcompat:1.4.2")
        implementation("com.squareup.okio:okio:3.2.0")

        implementation(project(":multiplatformAndroidJvmIosLibrary"))
        implementation(project(":multiplatformJvmLibrary"))
        implementation(project(":multiplatformAndroidLibrary"))
        implementation(project(":jvmLibrary"))
    }
}
