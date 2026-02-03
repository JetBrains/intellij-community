// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(p1: Int) { }
fun foo(p1: Int, p2: Int) { }

fun bar(ppp: Int, a: Int) {
    foo(<caret>)
}

// ELEMENT: ppp
// CHAR: ','
