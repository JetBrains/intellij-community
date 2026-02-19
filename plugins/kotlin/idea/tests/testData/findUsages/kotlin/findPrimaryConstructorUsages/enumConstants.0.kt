// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtPrimaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor AB(Int)"
enum class AB<caret>(p: Int) {
    A(1),
    B("b");
    constructor(s: String): this(s.length)
}