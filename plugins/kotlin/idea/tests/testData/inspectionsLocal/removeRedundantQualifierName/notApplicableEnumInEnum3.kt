// PROBLEM: none
// WITH_STDLIB
enum class B() {
    ;

    fun test() {
        <caret>B.values()
    }
}