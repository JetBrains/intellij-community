fun foo(a: Int, b: String, c: String, d: Double) {}

fun bar(b: String, a: Int, c: String) {
    foo(<caret>)
}

// ELEMENT: "a, b, c"
// IGNORE_K1
