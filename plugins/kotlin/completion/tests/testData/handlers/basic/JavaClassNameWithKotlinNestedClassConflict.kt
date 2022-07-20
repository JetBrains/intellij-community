// FIR_IDENTICAL
// FIR_COMPARISON

package one

class Outer {
    class JavaClass
    fun t(n: Java<caret>) {
    }
}

// ELEMENT: JavaClass
// TAIL_TEXT: " (one)"