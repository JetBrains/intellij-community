fun foo(element: Int): String = ""

fun bar(element: Int): Int = 2

fun test() {
    val element = 1
    foo(bar(element.<caret>)).length
}

// ELEMENT: dec
// CHAR: '\t'