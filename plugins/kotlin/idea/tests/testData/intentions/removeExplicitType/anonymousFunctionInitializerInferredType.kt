// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable T
// ERROR: Unresolved reference: t
// IGNORE_K1
fun <T> test(): T = t

val foo: <caret>() -> String = fun() = test()