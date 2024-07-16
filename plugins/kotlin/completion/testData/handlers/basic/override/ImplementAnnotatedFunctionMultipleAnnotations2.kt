// RETAIN_OVERRIDE_ANNOTATIONS: "dependency.Annotation1,dependency.Annotation3"
// FIR_IDENTICAL
// FIR_COMPARISON
import dependency.Annotation1
import dependency.Annotation2
import dependency.Annotation3

interface I {
    @Annotation1
    @Annotation2
    @Annotation3
    fun foo(p: Int)
}

class A : I {
    o<caret>
}

// ELEMENT_TEXT: "override fun foo(p: Int) {...}"
