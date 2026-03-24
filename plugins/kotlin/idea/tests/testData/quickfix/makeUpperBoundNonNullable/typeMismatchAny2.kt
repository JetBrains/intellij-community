// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: Argument type mismatch: actual type is 'T (of class Bar<T>)', but 'uninferred R (of fun <R> foo)' was expected.
// K2_ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.

fun <R> foo(x: R & Any) {}

class Bar<T> {
    fun bar(x: T) {
        foo(<caret>x)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.HighPriorityMakeUpperBoundNonNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix