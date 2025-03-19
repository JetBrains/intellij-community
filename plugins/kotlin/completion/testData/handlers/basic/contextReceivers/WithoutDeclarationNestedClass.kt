package test

class A {
    class Nested
}

context(Neste<caret>)

// ELEMENT: Nested
// FIR_COMPARISON
// FIR_IDENTICAL
// IGNORE_K1