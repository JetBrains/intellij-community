// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package client

import server.foo

fun test() {
    foo<caret>()
    foo(1)
    val t = foo
}
