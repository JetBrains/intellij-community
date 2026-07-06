plugins {
    {{android_library_plugin_id}}
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    {{iosTargetPlaceholder}}

    val commonMain by sourceSets.getting
    val androidMain by sourceSets.getting

    commonMain.dependencies {
        api("io.ktor:ktor-client-core:2.1.3")
    }

    iosMain.dependencies {
        compileOnly("com.squareup.okio:okio:3.3.0")
    }

    androidMain.dependencies {
        compileOnly("io.reactivex.rxjava3:rxjava:3.1.5")
    }
}
