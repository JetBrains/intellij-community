// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) {
        println(x)
    } else {
        println(y)
    }
}

fun println(s: String) {}