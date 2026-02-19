// LANGUAGE_VERSION: 1.2
// PROBLEM: none
// K2_ERROR: Annotation argument must be a compile-time constant.
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'Array<String>' was expected.
// ERROR: Type mismatch: inferred type is Int but Array<String> was expected

annotation class Some(val arg: Array<String>)

fun create(x: Int) = x

@Some(arg = <caret>create(123))
class My