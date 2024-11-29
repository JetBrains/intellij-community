plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    ios()

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
