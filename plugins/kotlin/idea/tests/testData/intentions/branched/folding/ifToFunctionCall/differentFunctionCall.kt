// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) println(x) else print(y)
}

fun println(s: String) {}
fun print(s: String) {}