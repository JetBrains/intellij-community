// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun test(b: Boolean): Int {
    <caret>if (b) {
        println(1)
        return 1
    } else {
        println(2)
        return 2
    }
}

fun println(i: Int) {}
