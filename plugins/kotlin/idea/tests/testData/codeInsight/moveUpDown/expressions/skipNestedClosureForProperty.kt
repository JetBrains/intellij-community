// MOVE: down
fun foo() {
    val <caret>y = ""
    val x = run(1, 2) {
        println("bar")
    }
}