fun bar() {
    val handler = { p<caret>
        foo()
    }
}

// IGNORE_K2
// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: ' '
