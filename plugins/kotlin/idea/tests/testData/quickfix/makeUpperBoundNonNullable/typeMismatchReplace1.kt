// "Change the upper bound of T to 'Foo' to make T non-nullable" "true"
// LANGUAGE_VERSION: 1.7

class Foo

fun <R> foo(x: R & Any) {}

fun <T : Foo?> bar(x: T) {
    foo(<caret>x)
}
