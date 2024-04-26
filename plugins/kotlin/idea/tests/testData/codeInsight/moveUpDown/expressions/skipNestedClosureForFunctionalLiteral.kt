// MOVE: down
fun foo() {
    <caret>{}
    val x = run(1, 2) {
        println("bar")
    }
}