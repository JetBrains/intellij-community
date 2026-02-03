fun foo(element: Int): String = ""

fun test() {
    val element = 1
    foo(element.<caret>).length
}

// ELEMENT: dec
// CHAR: '\t'