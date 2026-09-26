// WITH_STDLIB
fun println(s: String) {}

fun test(i: Int) {
    <caret>if (i == 1) {
        // comment
        // comment
        println("a")
    }
    else if (i == 2) println("b")
    else if (i in listOf(3, 4, 5)) println("c")
    else println("none")
}