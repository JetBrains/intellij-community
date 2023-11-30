// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String) {
    <caret>println(if (b) {
        x
        x
    } else {
        y
    })
}

fun println(s: String) {}