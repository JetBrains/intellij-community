// WITH_STDLIB
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Variable 'a' is never used
class A {
    fun foo() {}
}
fun baz(a: A): Int = 1

fun test() {
    val a = A()
    a.foo()
    1 + <caret>baz(a)
    a.foo()
}