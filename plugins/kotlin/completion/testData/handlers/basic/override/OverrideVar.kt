// FIR_IDENTICAL
// FIR_COMPARISON
annotation class Annotation
open class B {
    @Annotation
    open var someVar: String = ""
}

class A : B {
    o<caret>
}

// ELEMENT_TEXT: "override var someVar: String"
