// FIR_COMPARISON
// FIR_IDENTICAL
class Outer {
    inner class Inner {
        fun String.foo() {
            t<caret>
        }
    }
}

// COMPLETION_TYPE: BASIC
// ELEMENT: *
// CHAR: @
