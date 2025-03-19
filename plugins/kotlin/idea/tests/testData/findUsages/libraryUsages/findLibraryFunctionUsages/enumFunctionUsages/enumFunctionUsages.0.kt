// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(Int): Int"
package usages

import library.E

fun test(e: E) {
    e.<caret>foo(4)
    E.A.foo(4)
    E.O.foo(3)
}
