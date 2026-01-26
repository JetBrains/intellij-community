import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER("descr='Run Test'")!>MacosUserInfoTest<!> {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testMacosUserInfo<!>() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on MacOS."))
    }
}
