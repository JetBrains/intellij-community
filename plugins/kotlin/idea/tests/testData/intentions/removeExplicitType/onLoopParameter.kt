// WITH_STDLIB
fun test() {
    for (<caret>x: Int in listOf(1, 2, 3)) {
        println(x)
    }
}