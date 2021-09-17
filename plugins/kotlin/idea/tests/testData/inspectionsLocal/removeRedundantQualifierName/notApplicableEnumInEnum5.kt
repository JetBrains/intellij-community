// PROBLEM: none
// WITH_RUNTIME
enum class E {
    A, B, C;

    companion object {
        val foo = <caret>E.valueOf("A")
    }
}