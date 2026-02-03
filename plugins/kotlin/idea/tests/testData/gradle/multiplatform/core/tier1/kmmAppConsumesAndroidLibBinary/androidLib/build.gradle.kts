plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

{{default_android_block}}

publishing {
    repositories {
        maven("$rootDir/repo")
    }

    afterEvaluate {
        publications {
            create<MavenPublication>("default") {
                from(components["release"])
            }
        }
    }
}
