// IS_APPLICABLE: false
// IGNORE_K1
fun <T> foo(): T = 1 as T

fun test(): <caret>Int = foo()