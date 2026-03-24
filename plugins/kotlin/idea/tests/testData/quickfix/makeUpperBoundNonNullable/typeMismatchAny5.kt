// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: Argument type mismatch: actual type is 'Foo<T#1 (of class Bar<T, U>), U#1 (of class Bar<T, U>)>', but 'Foo<uninferred T (of fun <T, U> foo), uninferred U (of fun <T, U> foo)>' was expected.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'U'. Specify it explicitly.

class Foo<T, U>

fun <T, U> foo(x: Foo<T & Any, U>) {}

class Bar<T, U> {
    fun bar(x: Foo<T, U>) {
        foo(<caret>x)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.HighPriorityMakeUpperBoundNonNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix