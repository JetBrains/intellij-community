plugins {
    {{android_library_plugin_id}}
    kotlin("multiplatform")
}

{{android_library_configuration_open}}
    compileSdk = {{compile_sdk_version}}
    namespace = "org.jetbrains.kotlin.smoke.multiplatformAndroidJvmIosLibrary"
    {{android_legacy_main_manifest}}
{{android_library_configuration_close}}

kotlin {
    {{android_target}}
    jvm()
    linuxX64()

    val commonMain by sourceSets.getting
    val androidMain by sourceSets.getting
    val jvmMain by sourceSets.getting
    val androidAndJvmMain by sourceSets.creating

    androidAndJvmMain.dependsOn(commonMain)
    jvmMain.dependsOn(androidAndJvmMain)
    androidMain.dependsOn(androidAndJvmMain)
}
