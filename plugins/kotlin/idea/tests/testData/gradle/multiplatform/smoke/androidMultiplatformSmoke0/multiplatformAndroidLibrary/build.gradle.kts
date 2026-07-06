plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{android_library_configuration_open}}
    compileSdk = {{compile_sdk_version}}
    namespace = "org.jetbrains.kotlin.smoke.multiplatformAndroidLibrary"
    {{android_legacy_main_manifest}}
{{android_library_configuration_close}}

kotlin {
    {{android_target}}

    val commonMain by sourceSets.getting
    commonMain.dependencies {
        implementation(kotlin("stdlib"))
        implementation(project(":multiplatformAndroidJvmIosLibrary"))
        implementation("com.squareup.okio:okio:3.2.0")
    }
}
