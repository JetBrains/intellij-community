// CONFIGURE_LIBRARY: JUnit
// REF: /src.fromKotlinTestToKotlinFile.1.kt
// REF: /src.fromKotlinTestToKotlinFile.2.kt
// REF: foo()
import junit.framework.TestCase

class <caret>FooUtilsTest : TestCase()

class FooUtilsTest2 : TestCase()