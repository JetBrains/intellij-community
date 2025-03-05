fun foo(enum: JavaEnum?) {}

fun bar() {
    foo(<caret>)
}

// EXIST: FOO
// EXIST: BAR
// INVOCATION_COUNT: 1