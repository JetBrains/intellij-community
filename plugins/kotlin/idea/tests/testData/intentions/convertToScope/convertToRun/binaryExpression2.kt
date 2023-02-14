// WITH_STDLIB
// AFTER-WARNING: Parameter 'x' is never used
class A {
    infix fun foo(x: Any) = A()
}

fun main() {
    val a = A()
    a.foo(0)
    a.foo(0)
    <caret>a.foo(0) foo 0
    a.foo(0)
    a.foo(0)
}