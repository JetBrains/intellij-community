// "Make A.foo open" "false"
// ACTION: Go To Super Method
// ERROR: This type is final, so it cannot be inherited from
// K2_AFTER_ERROR: This type is final, so it cannot be extended.
class A() {
    open fun foo() {}
}

class B : A() {
    override<caret> fun foo() { }
}