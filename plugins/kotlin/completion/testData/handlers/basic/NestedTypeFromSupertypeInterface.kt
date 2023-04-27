// FIR_IDENTICAL
// FIR_COMPARISON

interface MyInterface {
    class Nested
}

class Foo : MyInterface {
    val prop: Nes<caret>
}

// ELEMENT: Nested