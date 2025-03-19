// FIR_COMPARISON
// FIR_IDENTICAL
class A

class B {
    fun A.bar() {}
}

context(A, B)
fun test() {
    ba<caret>
}

// EXIST: bar