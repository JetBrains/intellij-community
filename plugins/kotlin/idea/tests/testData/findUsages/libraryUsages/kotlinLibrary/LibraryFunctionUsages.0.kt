// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
// FIND_BY_REF

package usages

import library.*

fun test() {
    val f = ::foo
    <caret>foo()
}

