fun foo(xxx: java.io.File?)

fun bar(x<caret>)

// ELEMENT_TEXT: xxx: File?
// FIR_COMPARISON
// FIR_IDENTICAL
