// AFTER-WARNING: Parameter 'v' is never used
fun <T> foo(v: T): Int = 1

fun test(): <caret>Int = foo(1)