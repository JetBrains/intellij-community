fun foo(
    foo: Int,
    bar: Int = <caret>,
    baz: Int = 0,
) {}

// EXIST: foo
// ABSENT: bar
// ABSENT: baz