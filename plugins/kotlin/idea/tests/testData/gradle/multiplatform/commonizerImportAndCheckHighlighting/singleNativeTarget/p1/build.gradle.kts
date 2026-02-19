import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
}

kotlin {
    when {
        HostManager.hostIsLinux -> linuxX64("native")
        HostManager.hostIsMac -> macosX64("native")
        HostManager.hostIsMingw -> mingwX64("native")
        else -> throw IllegalStateException("Unsupported host")
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}
