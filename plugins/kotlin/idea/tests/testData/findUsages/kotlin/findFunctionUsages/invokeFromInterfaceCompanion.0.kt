// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

interface Foo {
    companion object {
        operator fun inv<caret>oke() = object : Foo {}
    }
}

// FIR_COMPARISON