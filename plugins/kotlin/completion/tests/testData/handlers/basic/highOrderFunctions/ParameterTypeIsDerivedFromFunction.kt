// FIR_IDENTICAL
// FIR_COMPARISON
interface I : () -> Unit

fun foo(i: I){}

fun bar() {
    <caret>
}

// ELEMENT: foo
