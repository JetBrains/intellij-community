import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxSpecificTest {
    @Test
    fun testLinuxLog() {
        val testMessage = "Test message for Linux"
        val expected = "Linux LOG: $testMessage"

        val actual = getFormattedLogMessage(testMessage)
        writeLogMessage(
            testMessage
        )
        assertEquals(expected, actual)
    }
}
