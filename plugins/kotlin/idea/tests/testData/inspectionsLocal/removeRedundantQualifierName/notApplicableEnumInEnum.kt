// PROBLEM: none
// WITH_STDLIB
enum class A

enum class B() {
    ;

    fun test() {
        <caret>A.values()
    }
}