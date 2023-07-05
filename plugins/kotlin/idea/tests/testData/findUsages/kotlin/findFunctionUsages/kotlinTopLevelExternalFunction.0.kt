// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun A.extFun(): Unit"

package server
class A

fun A.<caret>extFun() {}
