// IS_APPLICABLE: false
// IGNORE_FE10
fun <T> foo(): T = 1 as T

fun test(): <caret>Int = foo()