plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion(30)

    val debug by buildTypes.getting
    debug.matchingFallbacks = listOf("debug", "release")
}

kotlin {
    jvm()
    ios()
    android()

    val commonMain by sourceSets.getting
    val jvmAndAndroidMain by sourceSets.creating
    val jvmMain by sourceSets.getting
    val androidMain by sourceSets.getting

    jvmAndAndroidMain.dependsOn(commonMain)
    jvmMain.dependsOn(jvmAndAndroidMain)
    androidMain.dependsOn(jvmAndAndroidMain)

    commonMain.dependencies {
        api("org.jetbrains.kotlinx:atomicfu:0.15.1")
        implementation("io.ktor:ktor-client-core:1.5.1")
        api("io.ktor:ktor-client-json:1.5.1")
        compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2-native-mt")
    }

    jvmAndAndroidMain.dependencies {
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.1")
        compileOnly("io.reactivex.rxjava3:rxjava:3.0.10")
    }

    androidMain.dependencies {
        runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.2")
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                this.freeCompilerArgs += "-Xskip-prerelease-check"
                this.freeCompilerArgs += "-Xskip-runtime-version-check"
                this.freeCompilerArgs += "-Xsuppress-version-warnings"
            }
        }
    }
}
