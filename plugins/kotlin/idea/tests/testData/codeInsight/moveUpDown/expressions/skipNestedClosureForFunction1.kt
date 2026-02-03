// MOVE: down
fun foo() {
    fun b<caret>ar() {}
    fun baz() {
        run(1, 2) {
            println("bar")
        }
    }
}