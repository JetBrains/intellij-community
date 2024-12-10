// IGNORE_K1

fun <T, U> foo(
    foo: T,
    bar: U,
    block: (foo: T, bar: U) -> Unit,
) {
    block(foo, bar)
}

fun bar() {
    foo(42, "") { foo, <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "bar", tailText: " -> ", allLookupStrings: "bar", typeText: "String" }