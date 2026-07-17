// PROBLEM: none

class Foo(
    @Suppress("CanBeParameter") <caret>val bar: String
) : RuntimeException("foo: $bar")