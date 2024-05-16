// FIR_IDENTICAL
// FIR_COMPARISON
package test

class AClass {
    companion object {}
}

fun foo() {
    bar(<caret>)
}

// ELEMENT: AClass
