fun foo(p: () -> Unit){}

fun bar() {
    foo(<caret>)
}

fun f(){}

// IGNORE_K2
// COMPLETION_TYPE: SMART
// ELEMENT: *
// CHAR: :
