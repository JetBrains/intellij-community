open class B {
    open fun foo(p: String){}

    fun foo(p: Int){}
}

interface I {
    fun foo(p: String)
}

class A : B(), I {
    override fun foo(p: String) {
        super<B><caret>.foo("")
    }
}