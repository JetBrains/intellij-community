import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    when {
        HostManager.hostIsMac -> macosX64("supported")
        HostManager.hostIsMingw -> mingwX64("supported")
        HostManager.hostIsLinux -> linuxX64("supported")
        else -> throw UnsupportedOperationException("Test is not supported on host ${HostManager.host}")
    }

    when {
        HostManager.hostIsMac -> linuxMips32("unsupported")
        HostManager.hostIsLinux -> macosX64("unsupported")
        HostManager.hostIsMingw -> macosX64("unsupported")
        else -> throw UnsupportedOperationException("Test is not supported on host ${HostManager.host}")
    }

    val commonMain by sourceSets.getting
    val supportedMain by sourceSets.getting
    val unsupportedMain by sourceSets.getting

    supportedMain.dependsOn(commonMain)
    unsupportedMain.dependsOn(commonMain)
}