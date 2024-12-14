// IGNORE_K1

fun <T, U> foo(
    foo: T,
    bar: U,
    action: Function2<T, U, Unit>,
) {
    action(foo, bar)
}

fun bar() {
    foo(42, 42) { i, <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "i1", tailText: " -> ", allLookupStrings: "i1", typeText: "Int" }