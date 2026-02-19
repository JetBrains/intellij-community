// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.6

fun <T> bar(x: T) {
    Foo<T>().foo(<caret>x)
}
