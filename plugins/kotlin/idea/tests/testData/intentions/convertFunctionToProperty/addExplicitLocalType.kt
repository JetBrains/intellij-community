// AFTER-WARNING: Variable 't' is never used
// PRIORITY: LOW
fun test() {
    class X

    class A(val n: Int) {
        fun <caret>foo() = X()
    }

    val t = A(1).foo()
}