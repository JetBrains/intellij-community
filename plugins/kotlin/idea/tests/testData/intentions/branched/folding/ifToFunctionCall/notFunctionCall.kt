// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(x) else y
}

fun println(s: String) {}