// ERROR: Too many arguments for public final fun foo(): Boolean defined in A
// K2_ERROR: Too many arguments for 'fun foo(): Boolean'.
// SHOULD_FAIL_WITH: Call with arguments will be skipped: foo(2)
// AFTER-WARNING: Variable 't' is never used
// K2_AFTER_ERROR: Too many arguments for 'fun foo(): Boolean'.
class A(val n: Int) {
    fun <caret>foo(): Boolean = n > 1
}

fun test() {
    val t = A(1).foo(2)
}