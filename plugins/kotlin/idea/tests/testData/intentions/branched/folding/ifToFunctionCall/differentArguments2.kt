// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(s = x) else println(t = y)
}

fun println(s: String = "", t: String = "") {}
