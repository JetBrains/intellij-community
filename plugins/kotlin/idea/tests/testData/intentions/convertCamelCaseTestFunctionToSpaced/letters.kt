// CONFIGURE_LIBRARY: JUnit
// NO_TEMPLATE_TESTING
import org.junit.Test

class A {
    @Test fun <caret>testTwoPlusTwoEqualsFour() {}
}

fun test() {
    A().testTwoPlusTwoEqualsFour()
}