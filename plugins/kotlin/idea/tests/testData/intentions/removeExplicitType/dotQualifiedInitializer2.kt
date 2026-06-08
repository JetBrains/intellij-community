// IS_APPLICABLE: false

fun <T> String.foo(): T = 1 as T

val s = ""

fun test(): <caret>Int = s.foo()