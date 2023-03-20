// IGNORE_FE10
// IS_APPLICABLE: false

interface I

inline fun <reified E : I> foo(a: Any?): E? = a as? E

fun box(a: Any?) = <caret>foo(a) ?: "OK"
