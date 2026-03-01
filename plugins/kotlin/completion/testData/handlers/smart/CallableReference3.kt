fun foo(p: () -> Unit){}

fun bar() {
    foo(::<caret>)
}

fun f(){}

// ELEMENT: f
// IGNORE_K2
