import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER{OSX}("descr='Run Test'")!>MacosUserInfoTest<!> {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun <!LINE_MARKER{OSX}("descr='Run Test'")!>testMacosUserInfo<!>() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on MacOS."))
    }
}
