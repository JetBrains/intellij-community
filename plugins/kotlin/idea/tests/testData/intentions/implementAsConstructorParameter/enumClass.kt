// WITH_STDLIB
// DISABLE-ERRORS
interface T<X> {
    val <caret>foo: X
}

enum class E : T<Int> {
    A, B, C
}