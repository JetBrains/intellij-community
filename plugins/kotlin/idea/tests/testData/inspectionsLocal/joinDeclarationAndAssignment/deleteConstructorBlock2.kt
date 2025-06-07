// DISABLE_ERRORS
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 'j' is never used
class A(i: Int, j: Int) {
    constructor(i: Int) : this(i, 2) {
        a = 1
    }

    val a<caret>: Int
}