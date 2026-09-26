package junit.framework

open class TestCase {
    fun assertEquals(expected: Any?, actual: Any?) {}
    fun assertEquals(message: String, expected: Any?, actual: Any?) {}
    fun assertSame(expected: Any?, actual: Any?) {}
}
