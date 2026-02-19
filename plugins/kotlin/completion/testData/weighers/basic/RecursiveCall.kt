// FIR_COMPARISON
// FIR_IDENTICAL
fun foo2() {
    foo<caret>
}
fun foo1() {}

// ORDER: foo2
// ORDER: foo1
