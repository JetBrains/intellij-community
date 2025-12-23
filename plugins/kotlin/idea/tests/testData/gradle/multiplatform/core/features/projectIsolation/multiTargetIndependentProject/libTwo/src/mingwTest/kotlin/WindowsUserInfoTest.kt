import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowsUserInfoTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testWindowsUserInfo() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on Windows."))
    }
}
