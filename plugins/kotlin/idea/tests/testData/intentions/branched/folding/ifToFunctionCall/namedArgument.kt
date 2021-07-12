fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(x) else println(s = y)
}

fun println(s: String) {}