// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>if (b) {
        println(x)
    } else {
        println(y)
        println(y)
    }
}

fun println(s: String) {}