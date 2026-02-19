fun foo(p: (String) -> Unit){}

fun bar() {
    foo(<caret>)
}

// WITH_ORDER
// EXIST: "{...}"
// EXIST: "{ s -> ... }"
// EXIST: "{ s: String -> ... }"

// IGNORE_K2
