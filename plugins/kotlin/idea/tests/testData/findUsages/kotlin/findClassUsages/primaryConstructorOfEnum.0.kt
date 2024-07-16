// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class E"
enum class <caret>E(b: Boolean) {
    A(false),
    B(true)
}