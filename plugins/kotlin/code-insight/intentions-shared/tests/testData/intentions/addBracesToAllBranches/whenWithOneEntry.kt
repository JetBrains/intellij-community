// IS_APPLICABLE: false
fun test(i: Int) {
    <caret>when (i) {
        1 -> println(1)
    }
}

fun println(i: Int) {}