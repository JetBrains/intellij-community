// AFTER-WARNING: Variable 'x' is never used
fun foo(n: Int) {
    val x = <caret>object {
        fun bar() = n
    }
}