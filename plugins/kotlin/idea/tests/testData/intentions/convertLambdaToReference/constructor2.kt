// AFTER-WARNING: Parameter 'x' is never used
class A {
    class B {}
}

fun foo(x: () -> A.B) {}

fun main() {
    foo <caret>{ A.B() }
}