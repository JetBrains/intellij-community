// MOVE: down
fun foo() {
    fun b<caret>ar() {}
    val x = run(1, 2) {
        println("bar")
    }
}