// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun Companion.invoke()"

class Foo(n: Int) {
    companion object
}

operator fun Foo.Companion.invo<caret>ke() = 1

