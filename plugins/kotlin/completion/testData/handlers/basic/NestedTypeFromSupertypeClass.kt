// FIR_IDENTICAL
// FIR_COMPARISON

open class MyBaseClass {
    class Nested
}

class Foo : MyBaseClass() {
    val prop: Nes<caret>
}

// ELEMENT: Nested