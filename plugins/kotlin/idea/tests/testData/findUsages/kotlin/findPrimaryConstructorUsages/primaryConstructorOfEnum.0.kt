// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtPrimaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor E(Boolean)"
enum class E<caret>(b: Boolean) {
    A(false),
    B(true)
}