// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: ""
package server.usages

import server.foo

fun test() {
    foo()
    foo(1)
    val t = foo
}
