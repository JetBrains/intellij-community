// AFTER-WARNING: The expression is unused
// AFTER-WARNING: Unnecessary safe call on a non-null receiver of type A?
class A(val x: Int?) {}
fun m(a: A?) {
    a?.x <caret>?: 42
}