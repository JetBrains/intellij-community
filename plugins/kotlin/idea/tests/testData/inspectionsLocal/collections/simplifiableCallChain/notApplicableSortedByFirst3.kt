// PROBLEM: none
// API_VERSION: 1.3
// WITH_STDLIB
data class Foo(val x: Int?)

fun main() {
    listOf(Foo(1), Foo(null), Foo(2)).<caret>sortedBy(fun(it: Foo): Int? {
        return it.x
    }).first()
}