// FIR_IDENTICAL
// FIR_COMPARISON
class Outer {
    inner class Inner {
        fun String.foo() {
            "$this<caret>"
        }
    }
}
// ELEMENT: this@Inner