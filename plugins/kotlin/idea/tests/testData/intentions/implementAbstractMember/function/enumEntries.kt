// WITH_STDLIB
//DISABLE-ERRORS
enum class E {
    A, B, C;

    abstract fun <caret>foo(x: Int): Int
}