// FIR_COMPARISON
package test

@TestAnnotation(value = [<caret>])
fun foo() {
}

// EXIST: FOO, BAR
// IGNORE_K1
