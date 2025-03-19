// INTENTION_TEXT: "Lift function call out of 'when'"
// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String) {
    <caret>when {
        b -> println(x)
        else -> println(y)
    }
}

fun println(s: String) {}