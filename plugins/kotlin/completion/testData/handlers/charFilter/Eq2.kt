// FIR_COMPARISON
// FIR_IDENTICAL
var vvv = 1
var vvv1 = 2

fun foo() {
    vvv<caret>
}

// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: =
