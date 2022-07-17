// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

private object SomeObject {
    fun action<caret>() {}
}
// FIR_COMPARISON