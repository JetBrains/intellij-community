package kmmApplication

fun use() {
    // refines = internal visibility
    commonMainInternal()

    // non-expect declaration from common are refined
    produceCommonMainExpect()?.iosSpecificApi()

    // no incompatible descriptors
    consumeCommonMainExpect(CommonMainExpect())

    // Kotlin/Native stdlib is imported and usable
    val x: CpuArchitecture = CpuArchitecture.ARM64
}
