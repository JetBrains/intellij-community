// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>println(if (b) x else y, if (b) y else x)
}

fun println(s: String, t: String) {}