// AFTER-WARNING: Parameter 'p1' is never used
// AFTER-WARNING: Parameter 'p2' is never used
object O {
    fun foo(): Boolean = true
    fun bar(): Boolean = true
}
fun foo(p1: Boolean, p2: Boolean) {
    if (!(<caret>O.foo() || O.bar())) return
}