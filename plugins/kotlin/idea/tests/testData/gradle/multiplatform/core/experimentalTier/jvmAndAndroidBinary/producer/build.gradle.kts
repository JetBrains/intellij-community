plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
    id("maven-publish")
}

{{default_android_block}}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}


kotlin {
    jvm()
    {{androidTargetPlaceholder}}
    {{android_target_publishing_snippet}}

    sourceSets {
        val commonMain by getting { }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
            {{android_main_kotlin_source_dirs}}
        }

        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)
        }
    }
}
