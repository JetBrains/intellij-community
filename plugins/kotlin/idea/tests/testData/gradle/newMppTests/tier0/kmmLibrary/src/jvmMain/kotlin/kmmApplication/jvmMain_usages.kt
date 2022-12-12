package kmmApplication

import java.io.File

fun use() {
    // refines = internal visibility
    commonMainInternal()

    // non-expect declaration from common are refined
    produceCommonMainExpect()?.jvmSpecificApi()

    // no incompatible descriptors
    consumeCommonMainExpect(CommonMainExpect())

    // Checking that descriptors from stdlib-common are refined correctly and compatible with their
    // platform counterparts
    val refinedException: java.lang.Exception? = stdlibExpectLikeClass()

    // JDK is imported and usable
    val file = File("")
}
