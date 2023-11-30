// IS_APPLICABLE: false
// WITH_STDLIB
// IGNORE_K1
fun <T> foo(): Map<String, Map<String, T>> = emptyMap()

fun test(): <caret>Map<String, Map<String, Int>> = foo()