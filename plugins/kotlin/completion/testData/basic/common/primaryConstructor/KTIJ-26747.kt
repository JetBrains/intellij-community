class Foo(
    foo: Int,
    bar: Int = <caret>,
    baz: Int = 0,
)

// EXIST: foo
// ABSENT: bar
// ABSENT: baz