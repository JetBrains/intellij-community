// FIR_COMPARISON
// FIR_IDENTICAL
fun <T> T.genericExt() {}
fun test() {
    JavaEnum.<caret>
}

// EXIST: valueOf
// EXIST: A
// ABSENT: genericExt
// ABSENT: toString