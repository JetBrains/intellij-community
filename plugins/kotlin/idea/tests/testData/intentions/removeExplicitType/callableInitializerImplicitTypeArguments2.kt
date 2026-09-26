// IS_APPLICABLE: false
// WITH_STDLIB

fun <T> foo(): Map<String, Map<String, T>> = emptyMap()

fun test(): <caret>Map<String, Map<String, Int>> = foo()