// "Add 'Any' as upper bound for B to make it non-nullable" "false"
// ACTION: Add 'Any' as upper bound for T to make it non-nullable
// ACTION: Add 'x =' to argument
// ACTION: Change parameter 'x' type of function 'foo' to 'Foo<T, U>'
// ACTION: Create function 'foo'
// ERROR: Type mismatch: inferred type is Foo<A, B> but Foo<A & Any, B> was expected
// LANGUAGE_VERSION: 1.7
// K2_ERROR: Argument type mismatch: actual type is 'Foo<A (of class Bar<A, B>), B (of class Bar<A, B>)>', but 'Foo<A (of class Bar<A, B>) & Any, B (of class Bar<A, B>)>' was expected.
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Foo<A (of class Bar<A, B>), B (of class Bar<A, B>)>', but 'Foo<A (of class Bar<A, B>) & Any, B (of class Bar<A, B>)>' was expected.

class Foo<T, U>

fun <T, U> foo(x: Foo<T & Any, U>) {}

class Bar<A, B> {
    fun bar(x: Foo<A, B>) {
        foo<A, B>(<caret>x)
    }
}
