// CONFIGURE_LIBRARY: JUnit
// REF: FooUtilsTest
// REF: FooUtilsTest2
@file:JvmName("FooUtils")
import junit.framework.TestCase

class Foo

fun <caret>foo() {

}

val x = 1

@JvmField val y = 2

class FooUtilsTest : TestCase() {

}