// PROBLEM: none
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.junit5)
}
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config = files("${rootProject.projectDir}/detekt.yml")
    autoCorrect = true
}
android {
    namespace = "org.jellyfin.mobile"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionName = project.getVersionName()
        versionCode = getVersionCode(versionName!!)
        setProperty("archivesBaseName", "jellyfin-android-v$versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    val releaseSigningConfig = SigningHelper.loadSigningConfig(project)?.let { config ->
        signingConfigs.create("release") {
            storeFile = config.storeFile
            storePassword = config.storePassword
            keyAlias = config.keyAlias
            keyPassword = config.keyPassword
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            aaptOptions.cruncherEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigningConfig
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            aaptOptions.cruncherEnabled = false
        }
    }
    flavorDimensions += "variant"
    productFlavors {
        register("libre") {
            dimension = "variant"
            buildConfigField("boolean", "IS_PROPRIETARY", "false")
        }
        register("proprietary") {
            dimension = "variant"
            buildConfigField("boolean", "IS_PROPRIETARY", "true")
            isDefault = true
        }
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    @Suppress("UnstableApiUsage")
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }
    <caret>kotlinOptions {
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}