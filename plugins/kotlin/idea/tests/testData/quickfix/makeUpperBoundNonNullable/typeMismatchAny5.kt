// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

class Foo<T, U>

fun <T, U> foo(x: Foo<T & Any, U>) {}

class Bar<T, U> {
    fun bar(x: Foo<T, U>) {
        foo(<caret>x)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.HighPriorityMakeUpperBoundNonNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix