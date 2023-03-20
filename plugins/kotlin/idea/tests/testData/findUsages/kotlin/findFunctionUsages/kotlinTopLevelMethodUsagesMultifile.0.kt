// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"
@file:[JvmName("RequestProcessor") JvmMultifileClass]

package server

fun <caret>processRequest() = "foo"

