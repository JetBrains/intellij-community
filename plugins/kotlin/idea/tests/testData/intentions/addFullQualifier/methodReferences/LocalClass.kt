// IS_APPLICABLE: false

package one.two.three

fun check() {
    class LocalClass {
        fun testFunction() = 42
    }

    val a = <caret>LocalClass::testFunction
}
