// "class org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix" "false"
// ERROR: Type argument is not within its bounds: should be subtype of 'Any'
// K2_AFTER_ERROR: UPPER_BOUND_VIOLATED
// K2_ERROR: UPPER_BOUND_VIOLATED

fun <T : Any> foo() = 1

fun <E : Any?> bar() = foo<E<caret>>()
