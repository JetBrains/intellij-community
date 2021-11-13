package one

class Outer {
    class Nested
    inner class Inner
    fun t(n: Nest<caret>) {
    }
}
class Nested

// ELEMENT: Nested
// TAIL_TEXT: " (one)"