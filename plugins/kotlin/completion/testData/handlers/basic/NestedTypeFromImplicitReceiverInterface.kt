// FIR_IDENTICAL
// FIR_COMPARISON

interface MyInterface {
    class Nested
}

fun MyInterface.foo() {
    val p: Nes<caret>
}

// ELEMENT: Nested
