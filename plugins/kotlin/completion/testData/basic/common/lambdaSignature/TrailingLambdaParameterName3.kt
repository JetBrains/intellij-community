// FIR_COMPARISON

fun <T, U> foo(
    foo: T,
    bar: U,
    action: Function2<T, U, Unit>,
) {
    action(foo, bar)
}

fun bar() {
    foo(42, 42) { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: "i, i ->"
// EXIST: "i: Int, i: Int ->"