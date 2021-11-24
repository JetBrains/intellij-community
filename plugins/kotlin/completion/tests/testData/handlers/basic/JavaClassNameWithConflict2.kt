package one
open class Outer {
    class Nested
    inner class Inner
}

class Other : Outer() {
    fun t(n: Nested, i: Inner, completionType: Nest<caret>) {

    }
}

// ELEMENT: Nested
// TAIL_TEXT: " (three)"