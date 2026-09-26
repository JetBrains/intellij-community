package jvmmodule

import org.junit.jupiter.api.DisplayName
import testutils.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals


class <!LINE_MARKER("descr='Run Test'")!>JvmTest<!> {
    @Test
    @DisplayName("Should use test utils in JVM module")
    fun <!LINE_MARKER("descr='Run Test'")!>shouldUseTestUtilsInJvmModule<!>() {
        val message = TestHelper.formatTestMessage(" From JVM  ")
        assertEquals("Test Message: From JVM", message)

        val sum = TestHelper.sumNumbers(10, 20, 30)
        assertEquals(60, sum)

        val reversed = TestHelper.reverseString("IDEA")
        assertEquals("AEDI", reversed)
    }
}