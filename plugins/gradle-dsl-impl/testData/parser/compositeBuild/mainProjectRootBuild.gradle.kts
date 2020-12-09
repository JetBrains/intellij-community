// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    apply(from="applied.gradle.kts")
    repositories {
        maven { url = uri("/Volumes/android/studio-master-dev/out/repo/") }
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0-dev")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlin_version"]}")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

extra["mainProjectProperty"] = false

allprojects {
    repositories {
        google()
        jcenter()
    }
}

task("clean", type=Delete::class) {
    delete(rootProject.buildDir)
}
