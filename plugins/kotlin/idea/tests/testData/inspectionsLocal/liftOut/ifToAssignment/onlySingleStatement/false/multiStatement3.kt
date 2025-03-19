// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun test(b: Boolean) {
    var x: Int
    <caret>if (b) {
        println(1)
        x = 1
    } else {
        println(2)
        x = 2
    }
}

fun println(i: Int) {}
