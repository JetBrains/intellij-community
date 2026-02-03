// CONFIGURE_LIBRARY: JUnit
// REF: FooUtilsTest
@file:JvmName("FooUtils")
import junit.framework.TestCase

fun foo<caret>() { }

class FooUtilsTest : TestCase() {
    fun testFoo() { }
}