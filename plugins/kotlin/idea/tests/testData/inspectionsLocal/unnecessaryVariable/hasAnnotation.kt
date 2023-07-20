// PROBLEM: none

fun foo(x: Int): Int {
    @Ann
    val <caret>y = x
    return y + y
}

annotation class Ann