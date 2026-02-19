// IS_APPLICABLE: false
// PROBLEM: none

open class B {
    open fun foo(p: String){}
}

interface I {
    fun foo(p: String) {}
}

class A : B(), I {
    override fun foo(p: String) {
        super<B><caret>.foo("")
    }
}