import org.jetbrains.kotlin.com.intellij.openapi.util.SystemInfo

plugins {
    kotlin("multiplatform")
}

kotlin {
    when {
        SystemInfo.isLinux -> linuxX64("native")
        SystemInfo.isMac -> macosX64("native")
        SystemInfo.isWindows -> mingwX64("native")
        else -> throw IllegalStateException("Unsupported host")
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}
