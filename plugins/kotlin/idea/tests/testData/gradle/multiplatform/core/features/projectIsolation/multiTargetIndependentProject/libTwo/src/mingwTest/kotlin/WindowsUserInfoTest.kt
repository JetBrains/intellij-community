import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER("descr='Run Test'")!>WindowsUserInfoTest<!> {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testWindowsUserInfo<!>() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on Windows."))
    }
}
