// "Add 'Any' as upper bound for T to make it non-nullable" "true"
// WARNING: Type mismatch: inferred type is T but T & Any was expected
// LANGUAGE_VERSION: 1.8

fun <T : Any> bar(x: T) {
    Foo<T>().foo(x)
}
