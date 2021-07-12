fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(x) else println(y)
}

fun println(s: String) {}