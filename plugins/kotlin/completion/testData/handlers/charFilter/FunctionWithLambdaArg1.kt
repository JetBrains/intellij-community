// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(filter: (String) -> Boolean) {}

fun bar() {
    f<caret>
}

// ELEMENT: foo
// CHAR: {
