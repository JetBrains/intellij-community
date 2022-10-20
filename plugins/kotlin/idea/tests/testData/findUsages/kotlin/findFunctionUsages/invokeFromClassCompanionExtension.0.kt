// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

class Foo(n: Int) {
    companion object
}

operator fun Foo.Companion.invo<caret>ke() = 1

// FIR_COMPARISON