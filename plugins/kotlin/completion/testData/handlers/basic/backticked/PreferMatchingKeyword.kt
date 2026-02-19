infix fun Any.`as`(s: String): String{}

fun foo(p: Any) {
    p as<caret>
}

// IGNORE_K2
// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: ' '
