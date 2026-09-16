// "Add 'Any' as upper bound for E" "true"
// K2_ERROR: UPPER_BOUND_VIOLATED

fun <T : Any> foo() = 1

fun <E> bar() = foo<E<caret>>()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix