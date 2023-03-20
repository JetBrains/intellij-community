// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun action(): Unit"

private object SomeObject {
    fun action<caret>() {}
}
