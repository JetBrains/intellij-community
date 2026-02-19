plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}

{{default_android_block}}

kotlin {
    {{iosTargetPlaceHolder}}
    {{androidTargetPlaceholder}} {
        publishLibraryVariants("release", "debug")
    }
}
