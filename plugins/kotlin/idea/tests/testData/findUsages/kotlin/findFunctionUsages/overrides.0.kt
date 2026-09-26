// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "fun foo(String): Unit"
open class A {
    open fun <caret>foo(t: String) {
        println(t)
    }
}

