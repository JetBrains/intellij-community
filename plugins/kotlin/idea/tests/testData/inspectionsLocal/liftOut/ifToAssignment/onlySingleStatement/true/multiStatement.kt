// HIGHLIGHT: INFORMATION
fun test(b: Boolean) {
    var x: Int
    <caret>if (b) {
        println(1)
        x = 1
    } else {
        x = 2
    }
}

fun println(i: Int) {}
