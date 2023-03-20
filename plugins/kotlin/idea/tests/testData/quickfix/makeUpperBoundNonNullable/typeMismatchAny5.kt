// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7

class Foo<T, U>

fun <T, U> foo(x: Foo<T & Any, U>) {}

class Bar<T, U> {
    fun bar(x: Foo<T, U>) {
        foo(<caret>x)
    }
}
