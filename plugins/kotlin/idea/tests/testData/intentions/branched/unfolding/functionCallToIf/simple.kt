fun test(b: Boolean, x: String, y: String) {
    <caret>println(if (b) x else y)
}

fun println(s: String) {}