// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String) {
    <caret>println(if (b) {
        x
    } else {
        y
    })
}

fun println(s: String) {}