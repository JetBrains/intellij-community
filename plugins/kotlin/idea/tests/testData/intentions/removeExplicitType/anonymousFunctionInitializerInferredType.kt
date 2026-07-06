// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable T
// ERROR: Unresolved reference: t
// K2_ERROR: UNRESOLVED_REFERENCE

fun <T> test(): T = t

val foo: <caret>() -> String = fun() = test()