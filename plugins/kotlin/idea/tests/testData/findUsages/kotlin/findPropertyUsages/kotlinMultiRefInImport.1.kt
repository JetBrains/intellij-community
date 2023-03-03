// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: ""
package client

import server.foo

fun test() {
    foo()
    val t = 1.foo + foo
}
