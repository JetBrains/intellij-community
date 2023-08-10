package kmmApplication

// Android SDK is imported and usable
import android.os.Bundle

fun use() {
    // refines = internal visibility
    commonMainInternal()

    // non-expect declaration from common are refined
    produceCommonMainExpect()?.androidSpecificApi()

    // no incompatible descriptors
    consumeCommonMainExpect(CommonMainExpect())

    // Checking that descriptors from stdlib-common are refined correctly and compatible with their
    // platform counterparts
    val refinedException: java.lang.Exception? = stdlibExpectLikeClass()

    // Android SDK is imported and usable
    val bundle: Bundle? = null
}
