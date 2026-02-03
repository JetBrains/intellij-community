import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class <!LINE_MARKER("descr='Run Test'")!>JvmSpecificTest<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testJvmLog<!>() {
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        writeLogMessage("Test message for JVM")
        System.setOut(System.out)
        val output = outputStream.toString().trim()
        assertEquals("JVM LOG: Test message for JVM", output)
    }
}
