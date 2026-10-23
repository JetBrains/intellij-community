// IS_APPLICABLE: false
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

fun f(x: Int): Int = x

val x = (1 to 2).let { [first] -> <caret>f(first) }
