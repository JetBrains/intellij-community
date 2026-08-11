// "Make A.foo open" "false"
// ACTION: Go To Super Method
// ERROR: This type is final, so it cannot be inherited from
// K2_AFTER_ERROR: FINAL_SUPERTYPE
// K2_ERROR: FINAL_SUPERTYPE
class A() {
    open fun foo() {}
}

class B : A() {
    override<caret> fun foo() { }
}