// IS_APPLICABLE: false

fun <T> foo(): T = 1 as T

fun test(): <caret>Int = foo()