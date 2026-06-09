// INTENTION_TEXT: "Replace function call with 'when'"
// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String) {
    <caret>println(
        when {
            b -> x
            else -> y
        }
    )
}

fun println(s: String) {}