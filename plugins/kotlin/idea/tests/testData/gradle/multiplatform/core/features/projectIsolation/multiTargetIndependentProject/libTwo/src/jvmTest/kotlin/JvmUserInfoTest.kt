import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER("descr='Run Test'")!>JvmUserInfoTest<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testJvmUserInfo<!>() {
        val message = getUserPlatformInfo()
        assertTrue(message.startsWith("You're on JVM."))
        assertTrue(message.contains("Home directory:"))
    }
}
