import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

class MacosUserInfoTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testMacosUserInfo() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on MacOS."))
    }
}
