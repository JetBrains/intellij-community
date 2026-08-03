// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// RESTRICT_TO_USE_SCOPE

private interface FooProvider {
    fun <caret>foo() {}
}

class FooScope : FooProvider
