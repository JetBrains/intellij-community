package testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class <!LINE_MARKER("descr='Run Test'")!>TestHelperTest<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`should format test message correctly`<!>() {
        val result = TestHelper.formatTestMessage("   Hello    World   ")
        assertEquals("Test Message: Hello World", result)
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`should sum numbers correctly`<!>() {
        val result = TestHelper.sumNumbers(1, 2, 3, 4, 5)
        assertEquals(15, result)
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`should sum negative numbers correctly`<!>() {
        val result = TestHelper.sumNumbers(-10, -5, 15, 0)
        assertEquals(0, result)
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`should reverse string correctly`<!>() {
        val result = TestHelper.reverseString("Kotlin")
        assertEquals("niltok", result)
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`should detect palindromes correctly`<!>() {
        assertTrue(TestHelper.isPalindrome("madam"))
        assertTrue(TestHelper.isPalindrome("Racecar"))
        assertTrue(TestHelper.isPalindrome("A man a plan a canal Panama"))
    }

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`should detect non-palindromes correctly`<!>() {
        assertFalse(TestHelper.isPalindrome("Hello"))
        assertFalse(TestHelper.isPalindrome("Not a palindrome"))
    }
}