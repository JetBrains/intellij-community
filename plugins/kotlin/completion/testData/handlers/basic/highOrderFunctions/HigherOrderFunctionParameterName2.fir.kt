// FIR_COMPARISON

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

// ELEMENT: foo
// ITEM_TEXT: "foo, bar"
// TAIL_TEXT: " -> "
// TYPE_TEXT: "(String, Int)"