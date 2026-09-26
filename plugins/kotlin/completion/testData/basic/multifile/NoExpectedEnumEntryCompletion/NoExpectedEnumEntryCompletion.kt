fun foo(any: Any) {}

fun bar() {
    foo(<caret>)
}

// ABSENT: FOO
// ABSENT: BAR
// INVOCATION_COUNT: 1