// FIR_COMPARISON
package test

@TestAnnotation(<caret>)
fun foo() {
}

// EXIST: FOO, BAR
// IGNORE_K1
