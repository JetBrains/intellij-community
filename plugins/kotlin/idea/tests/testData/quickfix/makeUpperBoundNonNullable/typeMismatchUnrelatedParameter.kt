// "Add 'Any' as upper bound for B to make it non-nullable" "false"
// ACTION: Add 'Any' as upper bound for T to make it non-nullable
// ACTION: Add 'x =' to argument
// ACTION: Change parameter 'x' type of function 'foo' to 'Foo<T, U>'
// ACTION: Create function 'foo'
// ERROR: Type mismatch: inferred type is Foo<A, B> but Foo<A & Any, B> was expected
// LANGUAGE_VERSION: 1.7
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

class Foo<T, U>

fun <T, U> foo(x: Foo<T & Any, U>) {}

class Bar<A, B> {
    fun bar(x: Foo<A, B>) {
        foo<A, B>(<caret>x)
    }
}
