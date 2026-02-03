plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

group = "a"
version = "1.0"

repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

kotlin {
    iosArm64 {
        val main by compilations.getting
        val customTest by compilations.creating {
            associateWith(main)
        }
        val customNonTest by compilations.creating
    }

    {{androidTargetPlaceholder}}
}