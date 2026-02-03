// HIGHLIGHT: INFORMATION
fun test(b: Boolean) {
    var x: Int
    <caret>if (b) {
        x = 1
    } else {
        println(2)
        x = 2
    }
}

fun println(i: Int) {}
