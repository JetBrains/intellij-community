// FIR_COMPARISON
// FIR_IDENTICAL
// IGNORE_K1

class A(val xxx: Int) {
    class Nested
}

typealias AA = A
typealias AAA = AA

fun usage() {
    AAA::<caret>
}

// EXIST: xxx
// ABSENT: Nested
/* Access to nested classes through typealias isn't supported currently, see KT-34281 */