plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
}

kotlin {
    {{androidTargetPlaceholder}}

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared"))
            }
        }
    }
}

{{default_android_block}}