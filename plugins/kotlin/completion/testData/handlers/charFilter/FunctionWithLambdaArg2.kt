// IGNORE_K2
fun foo(filter: (String) -> Boolean) {}

fun bar() {
    f<caret>
}

// ELEMENT: foo
// CHAR: (
