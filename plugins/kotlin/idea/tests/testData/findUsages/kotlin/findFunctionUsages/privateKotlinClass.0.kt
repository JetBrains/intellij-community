// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun action(): Unit"

private class SomeClass {
    fun action<caret>() {}
}
