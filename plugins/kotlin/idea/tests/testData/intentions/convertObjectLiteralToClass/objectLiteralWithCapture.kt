// AFTER-WARNING: Variable 'x' is never used
open class K

fun foo(n: Int) {
    val x = <caret>object : K() {
        fun bar() = n
    }
}