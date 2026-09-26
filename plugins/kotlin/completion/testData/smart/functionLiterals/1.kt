fun foo(p: () -> Unit){}

fun bar() {
    foo(<caret>)
}

// EXIST: "{...}"
// ABSENT: "{ () -> ... }"

// IGNORE_K2
