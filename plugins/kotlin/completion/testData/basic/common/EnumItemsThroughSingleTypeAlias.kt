// FIR_COMPARISON
// FIR_IDENTICAL
enum class A {
    ONE;
    class B // Not allowed to resolve through typealiases, see KT-34281
}

typealias AA = A

fun usage() {
    AA.<caret>
}

// EXIST: ONE, values, valueOf
// ABSENT: B