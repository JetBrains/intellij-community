// IGNORE_K1
// IS_APPLICABLE: false
// K2_ERROR: Type argument for reified type parameter 'E' was inferred to the intersection of ['I' & 'String']. Reification of an intersection type results in the common supertype being used. This may lead to subtle issues and an explicit type argument is encouraged.

interface I

inline fun <reified E : I> foo(a: Any?): E? = a as? E

fun box(a: Any?) = <caret>foo(a) ?: "OK"
