// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.7

fun <T> foo(x: T & Any) {}

class Bar<T> {
    fun bar(xs: Collection<T>) {
        for (x in xs) {
            foo(<caret>x)
        }
    }
}
