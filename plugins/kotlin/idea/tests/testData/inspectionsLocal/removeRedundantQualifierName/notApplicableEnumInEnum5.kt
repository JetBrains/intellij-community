// PROBLEM: none
// WITH_STDLIB
enum class E {
    A, B, C;

    companion object {
        val foo = <caret>E.valueOf("A")
    }
}