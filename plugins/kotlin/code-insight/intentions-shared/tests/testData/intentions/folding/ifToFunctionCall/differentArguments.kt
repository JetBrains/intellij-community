// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(x, x) else println(y)
}

fun println(s: String t: String = "") {}
