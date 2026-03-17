// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: Argument type mismatch: actual type is 'T#1 (of class Bar<T>)', but 'uninferred T (of fun <T> foo)' was expected.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.

fun <T> foo(x: T & Any) {}

class Bar<T> {
    fun bar(xs: Collection<T>) {
        for (x in xs) {
            foo(<caret>x)
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.HighPriorityMakeUpperBoundNonNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix