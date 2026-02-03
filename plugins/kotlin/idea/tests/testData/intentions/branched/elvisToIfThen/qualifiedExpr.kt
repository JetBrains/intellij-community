// AFTER-WARNING: The expression is unused
class A(val x: Int?) {}
fun m(a: A) {
    a.x <caret>?: 42
}