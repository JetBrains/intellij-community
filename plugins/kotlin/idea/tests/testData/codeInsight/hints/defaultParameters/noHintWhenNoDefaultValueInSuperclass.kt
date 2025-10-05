open class A {
    open fun foo(a: Int) {}
}

class B : A() {
    override fun foo(a: Int) {}
}