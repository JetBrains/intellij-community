// FIR_COMPARISON
// FIR_IDENTICAL
enum class E {
    A {
        fun foo() {}

        fun test() {
            fo<caret>
        }
    }
}

// EXIST: foo