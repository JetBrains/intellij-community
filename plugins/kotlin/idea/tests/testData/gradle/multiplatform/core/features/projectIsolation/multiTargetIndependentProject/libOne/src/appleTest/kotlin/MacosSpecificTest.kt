import kotlin.test.Test
import kotlin.test.assertEquals

class <!LINE_MARKER{OSX}("descr='Run Test'")!>MacosSpecificTest<!> {
    @Test
    fun <!LINE_MARKER{OSX}("descr='Run Test'")!>testMacosLog<!>() {
        val testMessage = "Test message for MacOS"
        val expected = "MacOS LOG: $testMessage"

        val actual = getFormattedLogMessage(testMessage)
        writeLogMessage(
            testMessage
        )
        assertEquals(expected, actual)
    }
}
