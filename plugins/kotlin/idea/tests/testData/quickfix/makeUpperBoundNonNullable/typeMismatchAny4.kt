// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7

fun <T> foo(x: Collection<T & Any>) {}

class Bar<T> {
    fun bar(x: Collection<T>) {
        foo(<caret>x)
    }
}
