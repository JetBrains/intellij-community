// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

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