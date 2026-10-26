// RETAIN_OVERRIDE_ANNOTATIONS: "dependency.Annotation,dependency.OtherAnnotation,dependency.Aliased,dependency.Collision"
// FIR_IDENTICAL
// FIR_COMPARISON
import dependency.Annotation
import dependency.OtherAnnotation as Aliased

interface I {
    @Annotation
    @Aliased
    @dependency.Aliased
    @dependency.Collision
    fun foo(p: Int)
}

class A : I {
    @Annotation
    @Aliased
    @dependency.Other.Collision
    o<caret>
}

// ELEMENT_TEXT: "override fun foo(p: Int) {...}"
