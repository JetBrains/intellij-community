fun foo(a: Int, b: String, c: String) {}

// b here is not compatible, but the b that shadows this declaration in the function is
val b: String = ""

fun bar(a: Int, b: String, c: String) {
    foo(<caret>)
}

// EXIST: "a, b, c"