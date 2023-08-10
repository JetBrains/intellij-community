// FIR_IDENTICAL
open class A {
    open lateinit var vLateinit: String
    open operator fun contains(n: Int): Boolean = true
    open infix fun fInfix(n: Int) {}
    protected open external fun fProtected()
    internal open suspend fun fInternal() {}
    open fun fOverride() {}
}

open class B: A() {
    override fun fOverride() {
        super.fOverride()
    }
}

class C: B() {
    <caret>
}