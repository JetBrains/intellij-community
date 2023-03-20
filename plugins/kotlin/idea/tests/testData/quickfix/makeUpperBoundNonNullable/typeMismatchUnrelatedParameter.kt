// "Add 'Any' as upper bound for U to make it non-nullable" "false"
// ACTION: Add 'Any' as upper bound for T to make it non-nullable
// ACTION: Add 'x =' to argument
// ACTION: Change parameter 'x' type of function 'foo' to 'Foo<T, U>'
// ACTION: Create function 'foo'
// ERROR: Type mismatch: inferred type is Foo<T, U> but Foo<T & Any, U> was expected
// LANGUAGE_VERSION: 1.7

class Foo<T, U>

fun <T, U> foo(x: Foo<T & Any, U>) {}

class Bar<T, U> {
    fun bar(x: Foo<T, U>) {
        foo(<caret>x)
    }
}
