// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
interface A {
    fun <caret>foo(t: String)
}
