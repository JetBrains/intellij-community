// IGNORE_K1

fun <T, U> foo(
    foo: T,
    bar: U,
    block: (foo: T, bar: U) -> Unit,
) {
    block(foo, bar)
}

fun bar() {
    foo(42, "") { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "foo, bar", tailText: " -> ", allLookupStrings: "bar, foo", typeText: "(Int, String)" }