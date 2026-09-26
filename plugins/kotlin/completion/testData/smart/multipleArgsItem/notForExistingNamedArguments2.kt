fun f(a: Long, b: Int, c: String) {}

fun foo() {
    val a = 0L
    val b = 0
    f(c = "", <caret>)
}

// ABSENT: "a, b"
// ABSENT: "a, b, c"