// "Add 'Any' as upper bound for E" "true"
// K2_ERROR: Type argument is not within its bounds: must be subtype of 'Any'.

fun <T : Any> foo() = 1

fun <E> bar() = foo<E<caret>>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix