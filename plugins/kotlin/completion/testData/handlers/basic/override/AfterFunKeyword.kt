// FIR_COMPARISON
// FIR_IDENTICAL
interface I {
    fun foo(p: Int)
}

class A : I {
    override fun f<caret>
}

// ELEMENT_TEXT: "override fun foo(p: Int) {...}"
