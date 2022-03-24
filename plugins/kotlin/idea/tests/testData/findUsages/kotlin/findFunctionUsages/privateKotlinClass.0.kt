// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

private class SomeClass {
    fun action<caret>() {}
}
// FIR_COMPARISON