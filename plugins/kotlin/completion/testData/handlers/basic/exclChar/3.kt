// FIR_IDENTICAL
// FIR_COMPARISON
class C(val flag: Boolean)

fun foo(c: C) {
    if (c.<caret>)
}

// ELEMENT: flag
// CHAR: '!'
