// WITH_STDLIB
// DISABLE-ERRORS
interface T<X> {
    fun <caret>foo(x: X): X
}

enum class E : T<Int> {
    A, B, C
}