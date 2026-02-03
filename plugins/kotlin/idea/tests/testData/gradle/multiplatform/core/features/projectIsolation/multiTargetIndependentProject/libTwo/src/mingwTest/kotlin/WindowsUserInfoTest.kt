import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER{Windows}("descr='Run Test'")!>WindowsUserInfoTest<!> {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun <!LINE_MARKER{Windows}("descr='Run Test'")!>testWindowsUserInfo<!>() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on Windows."))
    }
}