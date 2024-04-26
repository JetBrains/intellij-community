fun baz(f: (a: Int, b: String, Int, String, Int, String) -> Unit) {
}

fun main() {
    baz<caret>
}

// IGNORE_K2
// ELEMENT: baz
// TAIL_TEXT: " { a, b, Int, String, Int, String -> ... } (f: (Int, String, Int, String, Int, String) -> Unit) (<root>)"