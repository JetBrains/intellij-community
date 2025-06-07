package mppmodule

import testutils.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals

class <!LINE_MARKER("descr='Run Test'")!>NativeTest<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>shouldFormatMessageCorrectlyInNativeTest<!>() {
        val message = TestHelper.formatTestMessage("  Native Works!   ")
        assertEquals("Test Message: Native Works!", message)
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>shouldSumNumbersInNativeTest<!>() {
        val sum = TestHelper.sumNumbers(3, 6, 9)
        assertEquals(18, sum)
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>shouldReverseStringInNativeTest<!>() {
        val reversed = TestHelper.reverseString("Native")
        assertEquals("evitan", reversed)
    }
}