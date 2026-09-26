fun foo(a: Int, b: String) {}

fun bar(b: String, a: Int) {
    foo(<caret>)
}

// ELEMENT: "a, b"
