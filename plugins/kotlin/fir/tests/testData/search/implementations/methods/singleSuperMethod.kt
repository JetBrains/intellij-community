open class A {
    open fun f<caret>oo(){}
}

class B : A() {
    override fun foo() {}
}