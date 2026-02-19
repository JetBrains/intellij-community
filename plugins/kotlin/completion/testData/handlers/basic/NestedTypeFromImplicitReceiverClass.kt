// FIR_IDENTICAL
// FIR_COMPARISON

class MyBaseClass {
    class Nested
}

fun MyBaseClass.foo() {
    val p: Nes<caret>
}

// ELEMENT: Nested