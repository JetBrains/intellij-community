// "Make A.foo open" "false"
// ACTION: Do not show return expression hints
// ACTION: Go To Super Method
// ERROR: This type is final, so it cannot be inherited from
class A() {
    open fun foo() {}
}

class B : A() {
    override<caret> fun foo() { }
}