// FIR_COMPARISON
// FIR_IDENTICAL
enum class A {
    ONE;
    class B // Not allowed to resolve through typealiases, see KT-34281
}

typealias AA = A
typealias AAA = AA

fun usage() {
    AAA.<caret>
}

// EXIST: ONE
// ABSENT: B