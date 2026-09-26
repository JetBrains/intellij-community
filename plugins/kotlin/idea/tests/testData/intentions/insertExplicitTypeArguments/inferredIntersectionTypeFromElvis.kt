// K2_ERROR: TYPE_INTERSECTION_AS_REIFIED_ERROR

// IS_APPLICABLE: false

interface I

inline fun <reified E : I> foo(a: Any?): E? = a as? E

fun box(a: Any?) = <caret>foo(a) ?: "OK"
