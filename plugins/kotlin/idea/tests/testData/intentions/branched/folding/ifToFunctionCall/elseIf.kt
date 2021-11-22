fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(x) else if (x == y) println(y) else println("")
}

fun println(s: String) {}