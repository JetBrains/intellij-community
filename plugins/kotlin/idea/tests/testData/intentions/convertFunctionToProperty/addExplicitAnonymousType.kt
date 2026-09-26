// AFTER-WARNING: Variable 't' is never used
// PRIORITY: LOW
interface T {
    fun bar()
}

class A(val n: Int) {
    fun <caret>foo() = object : T {
        override fun bar() {

        }
    }
}

fun test() {
    val t = A(1).foo()
}