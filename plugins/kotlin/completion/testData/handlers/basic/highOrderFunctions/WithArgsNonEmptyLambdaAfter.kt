fun foo(p : (String, Char) -> Boolean){}

fun main(args: Array<String>) {
    fo<caret>{ x }
}

// IGNORE_K2
// ELEMENT: foo
// TAIL_TEXT: " { String, Char -> ... } (p: (String, Char) -> Boolean) (<root>)"
