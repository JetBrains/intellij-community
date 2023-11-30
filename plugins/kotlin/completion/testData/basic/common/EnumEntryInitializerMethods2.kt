// FIR_COMPARISON
// FIR_IDENTICAL
enum class E {
    A {
        fun foo() {}
    }
}

fun test() {
    E.A.fo<caret>
}

// ABSENT: foo