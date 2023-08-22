// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun A.extFun(): Unit"
package client

import server.extFun
import server.A

fun test(a: A) {
    a.extFun<caret>()
}
