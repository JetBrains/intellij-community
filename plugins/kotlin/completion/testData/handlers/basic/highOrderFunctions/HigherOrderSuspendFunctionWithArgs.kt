fun foo(p : suspend (String, Char) -> Boolean){}
fun foo(p : suspend (String, Boolean) -> Boolean){}

fun main(args: Array<String>) {
    fo<caret>
}

// IGNORE_K2
// ELEMENT: foo
// TAIL_TEXT: " { String, Char -> ... } (p: suspend (String, Char) -> Boolean) (<root>)"
