// "Change the upper bound of T to 'A' to make T non-nullable" "true"
// LANGUAGE_VERSION: 1.7

class A

class Foo<T, U>

fun <T : Any, U> foo(x: Foo<T, U>) {}

class Bar<T : A?, U : A?> {
    fun bar(x: Foo<T, U>) {
        foo(<caret>x)
    }
}
