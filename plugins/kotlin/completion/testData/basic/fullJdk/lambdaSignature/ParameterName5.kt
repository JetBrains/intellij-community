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
    foo(42, "") { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "t, u", tailText: " -> ", allLookupStrings: "t, u", typeText: "(Int, String)" }