fun foo() {
    class A
    val a<caret> = A()
}
// AFTER-WARNING: Variable 'a' is never used
