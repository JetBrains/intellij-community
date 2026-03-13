fun f(a: Long, b: Int, c: String) {}

fun foo() {
    val b = 0
    val c = "0"
    f(a = 0L, <caret>)
}

// ABSENT: "b, c"
// ABSENT: "a, b, c"
// IGNORE_K1