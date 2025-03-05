// IGNORE_K1
// IGNORE_K2

fun <T, U> foo(
    foo: T,
    bar: U,
    action: java.util.function.BiConsumer<T, U>,
) {
    action.accept(foo, bar)
}

fun bar() {
    foo(42, "") { t, <caret> }
}

// INVOCATION_COUNT: 0
// TODO EXIST: { itemText: "u", tailText: " -> ", allLookupStrings: "u", typeText: "String" }