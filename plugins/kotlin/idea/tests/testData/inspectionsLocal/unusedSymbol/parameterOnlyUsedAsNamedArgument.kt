// PROBLEM: Parameter "aOne" is never used
// FIX: Safe delete parameter 'aOne'

fun op(aOne<caret>: String) {}

fun main() {
    op(aOne = "")
}

// FE10 generates compiler diagnostic to drop unused parameter
// IGNORE_K1