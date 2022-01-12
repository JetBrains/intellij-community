// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// ERROR: Type mismatch: inferred type is T but T & Any was expected
// LANGUAGE_VERSION: 1.8

fun <T> bar(x: T) {
    Foo<T>().foo(<caret>x)
}
