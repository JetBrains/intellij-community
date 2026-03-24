// "Change the upper bound of T to 'Foo' to make T non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: Argument type mismatch: actual type is 'T (of fun <T : Foo?> bar)', but 'uninferred R (of fun <R> foo)' was expected.
// K2_ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.

class Foo

fun <R> foo(x: R & Any) {}

fun <T : Foo?> bar(x: T) {
    foo(<caret>x)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.HighPriorityMakeUpperBoundNonNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix