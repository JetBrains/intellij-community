open class A {
    open fun foo(){}
}

class B : A() {
    override fun f<caret>oo() {}
}