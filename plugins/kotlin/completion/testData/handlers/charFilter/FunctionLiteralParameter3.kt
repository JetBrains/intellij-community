fun bar() {
    val handler = { p<caret>
        foo()
    }
}

// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: ' '
