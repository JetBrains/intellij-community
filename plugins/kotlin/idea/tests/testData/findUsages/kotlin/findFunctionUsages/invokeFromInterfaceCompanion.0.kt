// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun invoke()"

interface Foo {
    companion object {
        operator fun inv<caret>oke() = object : Foo {}
    }
}

