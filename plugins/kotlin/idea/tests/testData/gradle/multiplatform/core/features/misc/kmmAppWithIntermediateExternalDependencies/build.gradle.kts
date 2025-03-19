plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    ios()

    val commonMain by sourceSets.getting
    val androidMain by sourceSets.getting
    val iosMain by sourceSets.getting

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
