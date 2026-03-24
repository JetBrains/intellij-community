// "Add 'E' as upper bound for F" "true"
// K2_ERROR: Type argument is not within its bounds: must be subtype of 'E (of fun <E, F> bar)'.

fun <T, U : T> foo() = 1

fun <E, F> bar() = foo<E, F<caret>>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix