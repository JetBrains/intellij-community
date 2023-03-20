// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// LANGUAGE_VERSION: 1.6

fun <T : Any> bar(x: T) {
    Foo<T>().foo(x)
}
