fun foo(element: Int) : String = ""

class Bar {
    class Nested
}

fun test() {
    foo(Bar.Nes<caret>).length
}

// ELEMENT: Nested
// CHAR: '\t'