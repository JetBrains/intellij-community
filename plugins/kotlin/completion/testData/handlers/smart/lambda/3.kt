fun foo(p: (Int) -> Unit){}

fun bar() {
    foo(<caret>)
}

// ELEMENT: "{ i -> ... }"

// IGNORE_K2
