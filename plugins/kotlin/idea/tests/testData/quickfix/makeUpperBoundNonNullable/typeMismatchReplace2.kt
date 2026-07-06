// "Change the upper bound of T to 'A' to make T non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

class A

class Foo<T, U>

fun <T : Any, U> foo(x: Foo<T, U>) {}

class Bar<T : A?, U : A?> {
    fun bar(x: Foo<T, U>) {
        foo(<caret>x)
    }
}

// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.HighPriorityMakeUpperBoundNonNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix