// SHOULD_FAIL_WITH: Package 'foo.bar' already contains function f1(Int)
// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: Parameter 'n' is never used
package foo.bar

object Test {
    fun <caret>f1(n: Int) {}
}

fun f1(n: Int) {}
