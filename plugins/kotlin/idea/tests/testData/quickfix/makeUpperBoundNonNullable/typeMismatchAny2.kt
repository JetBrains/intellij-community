// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7

fun <R> foo(x: R & Any) {}

class Bar<T> {
    fun bar(x: T) {
        foo(<caret>x)
    }
}
