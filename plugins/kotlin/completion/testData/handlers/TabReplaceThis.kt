// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(s: String){ }

fun String.bar(sss: String) {
    foo(<caret>this)
}

// ELEMENT: sss
// CHAR: \t