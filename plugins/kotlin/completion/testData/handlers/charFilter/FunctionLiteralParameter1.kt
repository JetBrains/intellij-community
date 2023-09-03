fun foo(p: (String) -> Unit){}

fun bar() {
    foo { p<caret> }
}

// IGNORE_K2
// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: ' '
