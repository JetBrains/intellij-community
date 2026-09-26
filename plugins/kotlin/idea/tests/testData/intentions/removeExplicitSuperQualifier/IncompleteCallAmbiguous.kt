// IS_APPLICABLE: false
// PROBLEM: none
// ERROR: None of the following functions can be called with the arguments supplied: <br>public final fun foo(p: Int): Unit defined in B<br>public open fun foo(p: String): Unit defined in B
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NO_VALUE_FOR_PARAMETER

open class B {
    open fun foo(p: String){}
    fun foo(p: Int){}
}

interface I {
    fun foo(p: String)
}

class A : B(), I {
    override fun foo(p: String) {
        super<B><caret>.foo()
    }
}