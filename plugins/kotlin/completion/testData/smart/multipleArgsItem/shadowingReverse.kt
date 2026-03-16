fun foo(a: Int, b: String, c: String) {}

// b here is compatible, but the b that shadows this declaration in the function is not
val b: String = ""

fun bar(a: Int, b: Int, c: String) {
    foo(<caret>)
}

// ABSENT: "a, b, c"