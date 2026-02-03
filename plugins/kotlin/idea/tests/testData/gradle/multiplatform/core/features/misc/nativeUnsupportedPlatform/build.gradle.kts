import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform") version "{{kgp_version}}"
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
        HostManager.hostIsMac -> null
        HostManager.hostIsLinux -> macosX64("unsupported")
        HostManager.hostIsMingw -> macosX64("unsupported")
        else -> throw UnsupportedOperationException("Test is not supported on host ${HostManager.host}")
    }

    jvm() // just to avoid having only one target in the project; it causes some weird things with commonMain/commonTest

    val commonMain by sourceSets.getting
    val supportedMain by sourceSets.getting
    val unsupportedMain = sourceSets.findByName("unsupportedMain")

    supportedMain.dependsOn(commonMain)
    unsupportedMain?.dependsOn(commonMain)

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}
