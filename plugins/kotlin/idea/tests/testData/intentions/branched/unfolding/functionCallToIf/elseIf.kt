// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String) {
    <caret>println(if (b) x else if (x == y) y else "")
}

fun println(s: String) {}