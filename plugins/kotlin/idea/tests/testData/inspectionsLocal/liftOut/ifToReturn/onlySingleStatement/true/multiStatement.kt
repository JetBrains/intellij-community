// HIGHLIGHT: INFORMATION
fun test(b: Boolean): Int {
    <caret>if (b) {
        println(1)
        return 1
    } else {
        return 2
    }
}

fun println(i: Int) {}
