// FIR_IDENTICAL
// FIR_COMPARISON
import dependency.Annotation

interface I {
    @Annotation
    fun foo(p: Int)
}

class A : I {
    o<caret>
}

// ELEMENT_TEXT: "override fun foo(p: Int) {...}"
