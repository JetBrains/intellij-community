// HIGHLIGHT: INFORMATION
fun test(b: Boolean): Int {
    <caret>if (b) {
        return 1
    } else {
        println(2)
        return 2
    }
}

fun println(i: Int) {}
