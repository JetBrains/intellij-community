// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(xxx: Int, yyy: Int)

fun test() {
    foo(xxx = 10, <caret>)
}

// ELEMENT: "yyy ="
// CHAR: ' '
