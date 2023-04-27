plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion({{compile_sdk_version}})

    val debug by buildTypes.getting
    debug.matchingFallbacks += listOf("debug", "release")
}

kotlin {
    jvm()
    android()
    iosArm64()
    iosX64()

    val commonMain by sourceSets.getting
    val jvmAndAndroidMain by sourceSets.creating
    val jvmMain by sourceSets.getting
    val androidMain by sourceSets.getting
    val iosMain by sourceSets.creating
    val iosX64Main by sourceSets.getting
    val iosArm64Main by sourceSets.getting

    jvmAndAndroidMain.dependsOn(commonMain)
    jvmMain.dependsOn(jvmAndAndroidMain)
    androidMain.dependsOn(jvmAndAndroidMain)
    iosMain.dependsOn(commonMain)
    iosX64Main.dependsOn(iosMain)
    iosArm64Main.dependsOn(iosMain)

    commonMain.dependencies {
        api("org.jetbrains.kotlinx:atomicfu:0.19.0")
        implementation("io.ktor:ktor-client-core:2.2.1")
        compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    }

    jvmAndAndroidMain.dependencies {
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
        compileOnly("io.reactivex.rxjava3:rxjava:3.1.5")
    }

    androidMain.dependencies {
        runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                this.freeCompilerArgs += "-Xskip-prerelease-check"
                this.freeCompilerArgs += "-Xskip-metadata-version-check"
                this.freeCompilerArgs += "-Xskip-runtime-version-check"
                this.freeCompilerArgs += "-Xsuppress-version-warnings"
            }
        }
    }
}
