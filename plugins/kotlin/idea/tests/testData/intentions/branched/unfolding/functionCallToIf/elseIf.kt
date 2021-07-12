fun test(b: Boolean, x: String, y: String) {
    <caret>println(if (b) x else if (x == y) y else "")
}

fun println(s: String) {}