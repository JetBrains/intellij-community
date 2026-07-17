// LANGUAGE_VERSION: 1.2
// PROBLEM: none
// ERROR: Type mismatch: inferred type is Int but Array<String> was expected
// K2_ERROR: ANNOTATION_ARGUMENT_MUST_BE_CONST
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

annotation class Some(val arg: Array<String>)

fun create(x: Int) = x

@Some(arg = <caret>create(123))
class My