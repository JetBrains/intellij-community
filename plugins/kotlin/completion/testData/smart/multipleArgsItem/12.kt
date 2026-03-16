fun foo(a: Int, b: String, c: String) {}

val b: String = ""
fun bar(a: Int, c: String) {
    foo(<caret>)
}

// EXIST: "a, b, c"
