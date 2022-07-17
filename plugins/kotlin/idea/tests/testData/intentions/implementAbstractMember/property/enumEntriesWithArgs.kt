// WITH_STDLIB
//DISABLE-ERRORS
// AFTER-WARNING: Parameter 'n' is never used
enum class E(n: Int) {
    A(1), B(2), C(3);

    abstract val <caret>foo: Int
}