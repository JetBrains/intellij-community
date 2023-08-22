package kmmApplication

import platform.UIKit.UIDevice

fun use() {
    // refines = internal visibility
    commonMainInternal()

    // non-expect declaration from common are refined
    produceCommonMainExpect()?.iosSpecificApi()

    // no incompatible descriptors
    consumeCommonMainExpect(CommonMainExpect())

    // Kotlin/Native stdlib is imported and usable
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    val x: CpuArchitecture = CpuArchitecture.ARM64

    // iOS libraries are commonized, imported and usable
    // NB: Expected to be unresolved on non-Apple hosts
    val name: String = UIDevice.currentDevice.systemName()
}
