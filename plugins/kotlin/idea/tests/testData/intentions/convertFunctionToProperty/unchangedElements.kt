// WITH_STDLIB
// AFTER-WARNING: Variable 't' is never used
// PRIORITY: LOW

annotation class X(val s: String)

class A(val n: Int) {
    val t = 1
    internal @X("1") fun <T : Number> T.<caret>foo(): Boolean = toInt() - n > 1
    val u = 2
}

fun test() {
    val t = with(A(1)) {
        2.5.foo()
    }
}