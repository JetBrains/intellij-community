fun foo(p : (String, Char) -> Boolean){}

fun main(args: Array<String>) {
    fo<caret>
}

// IGNORE_K2
// ELEMENT: foo
// TAIL_TEXT: "(p: (String, Char) -> Boolean) (<root>)"
