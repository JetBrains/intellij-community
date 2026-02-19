// CONFIGURE_LIBRARY: JUnit
// REF: Foo
import junit.framework.TestCase

interface Foo

class <caret>FooTest : TestCase()

class FooTest2 : TestCase()