// "Add 'Any' as upper bound for E to make it non-nullable" "true"
// ERROR: Type mismatch: inferred type is F but Any was expected

fun <T : Any, U: Any> foo(x: T, y: U) = 1

fun <E, F> bar(x: E, y: F) = foo(<caret>x, y)
