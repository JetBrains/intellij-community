fun println(s: String) {}

fun test(x: Double, i: Int) {
    if (x > 0.0) {
        <caret>if (i == 1) {
            println("a")
        }
        else if (i == 2) {
            println("b")
        }
        else {
            println("none")
        }
    }
}