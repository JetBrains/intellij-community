// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"

package server

fun <caret>processRequest() = "foo"

