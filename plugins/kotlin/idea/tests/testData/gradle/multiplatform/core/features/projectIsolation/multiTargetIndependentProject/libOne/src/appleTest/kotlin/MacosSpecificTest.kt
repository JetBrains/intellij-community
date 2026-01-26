import kotlin.test.Test
import kotlin.test.assertEquals

class MacosSpecificTest {
    @Test
    fun testMacosLog() {
        val testMessage = "Test message for MacOS"
        val expected = "MacOS LOG: $testMessage"

        val actual = getFormattedLogMessage(testMessage)
        writeLogMessage(
            testMessage
        )
        assertEquals(expected, actual)
    }
}
