fun <T> foo(p : (T, Int) -> Boolean){}

fun main(args: Array<String>) {
    fo<caret>
}

// IGNORE_K2
// ELEMENT: foo
// TAIL_TEXT: " { T, Int -> ... } (p: (T, Int) -> Boolean) (<root>)"
