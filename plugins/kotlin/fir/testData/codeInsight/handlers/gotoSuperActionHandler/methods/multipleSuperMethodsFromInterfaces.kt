open class A {
    open fun foo(){}
}

interface I {
    fun foo()
}

class B : A(), I {
    override fun f<caret>oo() {}
}