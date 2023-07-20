// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "fun foo(String): Unit"
interface A {
    fun <caret>foo(t: String)
}

